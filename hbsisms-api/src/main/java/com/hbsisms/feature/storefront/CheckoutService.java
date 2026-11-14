package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.pricing.PriceResolver;
import com.hbsisms.feature.customer.pricing.ResolvedPrice;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.PayosLinkService;
import com.hbsisms.feature.sales.AllocationStatus;
import com.hbsisms.feature.sales.Delivery;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.OrderType;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderLine;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.SellUnit;
import com.hbsisms.feature.storefront.dto.CheckoutLineRequest;
import com.hbsisms.feature.storefront.dto.CheckoutPaymentResponse;
import com.hbsisms.feature.storefront.dto.CheckoutRequest;
import com.hbsisms.feature.storefront.dto.CheckoutResponse;
import com.hbsisms.feature.storefront.dto.ShortageItem;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 #27 — B2C checkout: stock check, ONLINE_B2C order, FEFO hold; PayOS link via #33. */
@Service
public class CheckoutService {

    private static final Set<String> TIME_WINDOWS =
            Set.of("08:00-10:00", "10:00-12:00", "14:00-16:00", "16:00-18:00");

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final CheckoutIdempotencyRepository idempotencyRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final ProductVariantRepository productVariantRepository;
    private final StockRepository stockRepository;
    private final PriceResolver priceResolver;
    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final DeliveryRepository deliveryRepository;
    private final OrderAllocationService orderAllocationService;
    private final PaymentRepository paymentRepository;
    private final SalesOrderService salesOrderService;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final AppConfigService appConfigService;
    private final PayosLinkService payosLinkService;
    private final Clock clock;

    public CheckoutService(
            CheckoutIdempotencyRepository idempotencyRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductVariantRepository productVariantRepository,
            StockRepository stockRepository,
            PriceResolver priceResolver,
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            DeliveryRepository deliveryRepository,
            OrderAllocationService orderAllocationService,
            PaymentRepository paymentRepository,
            SalesOrderService salesOrderService,
            CartService cartService,
            UserRepository userRepository,
            AppConfigService appConfigService,
            PayosLinkService payosLinkService,
            Clock clock) {
        this.idempotencyRepository = idempotencyRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.productVariantRepository = productVariantRepository;
        this.stockRepository = stockRepository;
        this.priceResolver = priceResolver;
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.deliveryRepository = deliveryRepository;
        this.orderAllocationService = orderAllocationService;
        this.paymentRepository = paymentRepository;
        this.salesOrderService = salesOrderService;
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.appConfigService = appConfigService;
        this.payosLinkService = payosLinkService;
        this.clock = clock;
    }

    @Transactional
    public CheckoutResponse checkout(
            Long userId, Customer customer, String idempotencyKey, CheckoutRequest request, String cartSessionRaw) {
        String key = Strings.trimToNull(idempotencyKey);
        if (key == null) {
            throw ApiException.badRequest("Thiếu header Idempotency-Key");
        }
        if (key.length() > 100) {
            throw ApiException.badRequest("Idempotency-Key tối đa 100 ký tự");
        }

        CheckoutIdempotency existing = idempotencyRepository.findByUserIdAndIdemKey(userId, key).orElse(null);
        if (existing != null) {
            return buildResponse(existing.getOrderId());
        }

        validateRequest(customer, request);

        LocalDate today = LocalDate.now(clock);
        List<ShortageItem> shortages = new ArrayList<>();
        List<PreparedLine> prepared = new ArrayList<>();
        BigDecimal subtotal = ZERO;

        for (CheckoutLineRequest line : request.lines()) {
            ProductVariant variant = requireSellableVariant(line.variantId());
            int qty = line.qtyKhay();
            long available = stockRepository.sumAvailableByVariantExcludingExpired(variant.getId(), today);
            if (available < qty) {
                shortages.add(new ShortageItem(variant.getId(), qty, (int) Math.max(0, available)));
                continue;
            }
            ResolvedPrice priced = priceResolver.resolve(customer.getId(), variant.getId(), qty);
            BigDecimal lineTotal = priced.unitPrice().multiply(BigDecimal.valueOf(qty));
            subtotal = subtotal.add(lineTotal);
            prepared.add(new PreparedLine(variant, qty, priced, lineTotal));
        }

        if (!shortages.isEmpty()) {
            throw ApiException.conflict("Một số sản phẩm không đủ hàng", shortages);
        }

        Instant now = Instant.now(clock);
        int holdMinutes = appConfigService.paymentHoldMinutes();
        Instant holdExpires = now.plusSeconds(holdMinutes * 60L);

        AppUser placedBy = userRepository.findById(userId).orElse(null);

        CustomerAddress shipAddress = null;
        if (request.fulfillmentMethod() == FulfillmentMethod.SHOP_DELIVERY) {
            shipAddress = customerAddressRepository
                    .findByIdAndCustomerId(request.shipAddressId(), customer.getId())
                    .orElseThrow(() -> ApiException.badRequest("Địa chỉ giao hàng không hợp lệ"));
        }

        SalesOrder order = new SalesOrder();
        order.setCode(nextOrderCode());
        order.setCustomer(customer);
        order.setOrderType(OrderType.ONLINE_B2C);
        order.setChannel(SaleChannel.WEB);
        order.setPaymentMethod(PaymentMethod.PAYOS);
        order.setPaymentTermDays((short) 0);
        order.setPaymentStatus(PaymentStatus.WAITING);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(request.fulfillmentMethod());
        order.setShipAddress(shipAddress);
        order.setState(OrderState.PENDING);
        order.setSubtotal(subtotal);
        order.setDiscountTotal(ZERO);
        // Giao tận nơi: cộng phí giao (app_config shipping_fee_delivery); nhận tại cửa hàng: 0
        BigDecimal shippingFee = request.fulfillmentMethod() == FulfillmentMethod.SHOP_DELIVERY
                ? BigDecimal.valueOf(appConfigService.shippingFeeDelivery())
                : ZERO;
        order.setShippingFee(shippingFee);
        order.setTotal(subtotal.add(shippingFee));
        order.setOverLimit(false);
        order.setOverLimitAmount(ZERO);
        order.setPlacedBy(placedBy);
        order.setPlacedAt(now);
        order.setHoldExpiresAt(holdExpires);
        order.setNote(Strings.trimToNull(request.note()));

        for (PreparedLine line : prepared) {
            SalesOrderLine row = new SalesOrderLine();
            row.setVariant(line.variant());
            row.setUnit(SellUnit.PACK);
            row.setQty(line.qtyKhay());
            row.setQtyKhay(line.qtyKhay());
            row.setUnitPrice(line.priced().unitPrice());
            row.setDiscountPct(ZERO);
            row.setLineTotal(line.lineTotal());
            row.setPriceBasis(line.priced().priceBasis());
            row.setOutstandingQty(0);
            order.addLine(row);
        }

        salesOrderRepository.save(order);
        addEvent(order, "created", "Tạo đơn online — chờ thanh toán PayOS", placedBy);

        Delivery delivery = new Delivery();
        delivery.setCode("DL-" + order.getCode());
        delivery.setOrder(order);
        delivery.setLegLabel("Trọn đơn");
        delivery.setScheduledDate(request.requestedDate());
        delivery.setTimeWindow(Strings.trimToNull(request.timeWindow()));
        delivery.setFulfillmentMethod(request.fulfillmentMethod());
        delivery.setAddress(shipAddress);
        if (shipAddress != null) {
            delivery.setInRadius(shipAddress.isInDeliveryRadius());
            delivery.setDistanceKm(shipAddress.getDistanceKm());
        } else {
            delivery.setInRadius(true);
        }
        delivery.setState(DeliveryState.READY);
        delivery.setItems(List.of());
        deliveryRepository.save(delivery);

        orderAllocationService.holdForOrder(order, userId);
        // Ensure hold window matches payment hold config (allocation may also set it).
        if (order.getHoldExpiresAt() == null) {
            order.setHoldExpiresAt(holdExpires);
        }
        salesOrderRepository.save(order);

        payosLinkService.createForCheckout(order, customer, holdExpires);

        clearCartQuietly(cartSessionRaw);

        CheckoutIdempotency idem = new CheckoutIdempotency();
        idem.setUserId(userId);
        idem.setIdemKey(key);
        idem.setOrderId(order.getId());
        idempotencyRepository.save(idem);

        return buildResponse(order.getId());
    }

    private CheckoutResponse buildResponse(Long orderId) {
        var orderDetail = salesOrderService.getDetail(orderId);
        Payment payment = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(orderId).stream()
                .filter(p -> p.getMethod() == PaymentMethod.PAYOS)
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thanh toán PayOS"));
        Instant expires = orderDetail.holdExpiresAt();
        CheckoutPaymentResponse pay = payosLinkService.toCheckoutPayment(payment, expires);
        return new CheckoutResponse(orderDetail, pay);
    }

    private void validateRequest(Customer customer, CheckoutRequest request) {
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể đặt hàng");
        }
        PaymentMethod method = request.paymentMethod() == null ? PaymentMethod.PAYOS : request.paymentMethod();
        if (method != PaymentMethod.PAYOS) {
            throw ApiException.badRequest("Checkout B2C hiện chỉ hỗ trợ PAYOS");
        }
        FulfillmentMethod fulfillment = request.fulfillmentMethod();
        if (fulfillment == FulfillmentMethod.SHOP_DELIVERY) {
            if (request.shipAddressId() == null) {
                throw ApiException.badRequest("Vui lòng chọn địa chỉ giao hàng");
            }
        } else if (fulfillment == FulfillmentMethod.PICKUP) {
            // shipAddressId ignored
        } else {
            throw ApiException.badRequest("Cách nhận hàng không hỗ trợ trên web khách");
        }
        if (request.requestedDate() == null) {
            throw ApiException.badRequest("Ngày nhận hàng là bắt buộc");
        }
        if (request.requestedDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("Ngày nhận hàng không được trong quá khứ");
        }
        String window = Strings.trimToNull(request.timeWindow());
        if (window == null || !TIME_WINDOWS.contains(window)) {
            throw ApiException.badRequest("Khung giờ không hợp lệ");
        }
    }

    private ProductVariant requireSellableVariant(Long variantId) {
        ProductVariant variant = productVariantRepository
                .findWithProductById(variantId)
                .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));
        if (!variant.isActive()) {
            throw ApiException.badRequest("Quy cách không còn bán");
        }
        Product product = variant.getProduct();
        if (product == null || !product.isActive() || !product.isPublished()) {
            throw ApiException.badRequest("Sản phẩm chưa được đăng bán công khai");
        }
        return variant;
    }

    private void clearCartQuietly(String cartSessionRaw) {
        String raw = Strings.trimToNull(cartSessionRaw);
        if (raw == null) {
            return;
        }
        try {
            UUID token = UUID.fromString(raw.trim());
            cartService.clear(token);
        } catch (IllegalArgumentException | ApiException ignored) {
            // Giỏ trống / session sai — không chặn checkout.
        }
    }

    private void addEvent(SalesOrder order, String eventType, String description, AppUser actor) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(eventType);
        event.setDescription(description);
        event.setCreatedBy(actor);
        orderEventRepository.save(event);
    }

    private String nextOrderCode() {
        String year = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));
        String prefix = "SO-" + year + "-";
        int next = salesOrderRepository.findCodesByPrefix(prefix).stream()
                .map(code -> code.substring(prefix.length()))
                .map(digits -> {
                    try {
                        return Integer.parseInt(digits);
                    } catch (NumberFormatException ex) {
                        return 0;
                    }
                })
                .max(Integer::compareTo)
                .orElse(0)
                + 1;
        return prefix + String.format(Locale.ROOT, "%04d", next);
    }

    private record PreparedLine(
            ProductVariant variant, int qtyKhay, ResolvedPrice priced, BigDecimal lineTotal) {}
}
