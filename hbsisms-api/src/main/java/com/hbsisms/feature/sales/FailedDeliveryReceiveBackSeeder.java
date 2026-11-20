package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.inventory.QuarantineLocationSeeder;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo task #29–30: đơn Chờ giao + đợt Thất bại còn HOLD, chưa có phiếu nhận về → FE bấm «Nhận về».
 * Idempotent theo mã {@code SO-DEMO-FAIL-NN}; nếu phiếu nhận về đã tạo thì seed slot tiếp theo (tối đa 5).
 */
@Component
@Order(70)
public class FailedDeliveryReceiveBackSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FailedDeliveryReceiveBackSeeder.class);
    private static final String ORDER_PREFIX = "SO-DEMO-FAIL-";
    private static final String CUSTOMER_CODE = "KH-001";
    private static final int QTY_KHAY = 2;
    private static final int MAX_SLOTS = 5;

    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryRepository deliveryRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final StockRepository stockRepository;
    private final OrderAllocationService orderAllocationService;
    private final UserRepository userRepository;
    private final OrderEventRepository orderEventRepository;
    private final Clock clock;

    public FailedDeliveryReceiveBackSeeder(
            SalesOrderRepository salesOrderRepository,
            DeliveryRepository deliveryRepository,
            SalesReturnRepository salesReturnRepository,
            CustomerRepository customerRepository,
            CustomerAddressRepository addressRepository,
            StockRepository stockRepository,
            OrderAllocationService orderAllocationService,
            UserRepository userRepository,
            OrderEventRepository orderEventRepository,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryRepository = deliveryRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.stockRepository = stockRepository;
        this.orderAllocationService = orderAllocationService;
        this.userRepository = userRepository;
        this.orderEventRepository = orderEventRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (int i = 1; i <= MAX_SLOTS; i++) {
            String orderCode = ORDER_PREFIX + String.format("%02d", i);
            String deliveryCode = "DL-" + orderCode;
            if (ensureSlot(orderCode, deliveryCode)) {
                return;
            }
        }
        log.warn(
                "Demo nhận về: đã có đủ {} slot {}* và đều đã tạo phiếu nhận về — xóa phiếu hoặc tăng MAX_SLOTS",
                MAX_SLOTS,
                ORDER_PREFIX);
    }

    /**
     * @return true nếu đã có (hoặc vừa tạo) một đợt FAILED còn nhận về được
     */
    private boolean ensureSlot(String orderCode, String deliveryCode) {
        SalesOrder existing = salesOrderRepository.findByCode(orderCode).orElse(null);
        if (existing != null) {
            Delivery delivery = deliveryRepository.findByOrder_IdOrderByIdAsc(existing.getId()).stream()
                    .filter(d -> deliveryCode.equals(d.getCode()) || d.getState() == DeliveryState.FAILED)
                    .findFirst()
                    .orElse(null);
            if (delivery == null || delivery.getState() != DeliveryState.FAILED) {
                log.info("Skip {}: order tồn tại nhưng không có đợt FAILED", orderCode);
                return false;
            }
            boolean alreadyReturned = !salesReturnRepository
                    .findByDelivery_IdAndStatusNot(delivery.getId(), ReturnStatus.REJECTED)
                    .isEmpty();
            if (alreadyReturned) {
                log.info(
                        "Demo nhận về {}: đã có phiếu — thử slot sau. FE: Trả hàng → Kiểm / nhận hàng",
                        orderCode);
                return false;
            }
            log.info(
                    "Demo nhận về sẵn sàng: đơn {} · đợt {} (id={}) — Giao hàng → Nhận về",
                    orderCode,
                    delivery.getCode(),
                    delivery.getId());
            return true;
        }

        Customer customer = customerRepository
                .findByCodeIgnoreCase(CUSTOMER_CODE)
                .or(() -> customerRepository.findAll().stream().findFirst())
                .orElse(null);
        if (customer == null) {
            log.warn("Skip demo nhận về {}: chưa có khách hàng", orderCode);
            return false;
        }

        Stock pick = stockRepository.findAllWithDetails().stream()
                .filter(s -> s.getQtyAvailable() >= QTY_KHAY)
                .filter(s -> s.getLocation() != null && !QuarantineLocationSeeder.isQuarantineZone(s.getLocation()))
                .findFirst()
                .orElse(null);
        if (pick == null) {
            log.warn("Skip demo nhận về {}: không đủ tồn khả dụng ≥ {} khay", orderCode, QTY_KHAY);
            return false;
        }
        ProductVariant variant = pick.getVariant();
        AppUser actor = userRepository.findAll().stream().findFirst().orElse(null);
        CustomerAddress ship = addressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(customer.getId())
                .stream()
                .findFirst()
                .orElse(null);

        Instant now = Instant.now(clock);
        BigDecimal unitPrice =
                variant.getListPrice() != null ? variant.getListPrice() : new BigDecimal("100000");
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(QTY_KHAY));

        SalesOrder order = new SalesOrder();
        order.setCode(orderCode);
        order.setCustomer(customer);
        order.setCustomerName(customer.getName());
        order.setOrderType(OrderType.B2B);
        order.setChannel(SaleChannel.PHONE);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentTermDays((short) 0);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        order.setShipAddress(ship);
        order.setState(OrderState.TO_SHIP);
        order.setSubtotal(lineTotal);
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotal(lineTotal);
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO);
        order.setPlacedBy(actor);
        order.setPlacedAt(now);
        order.setHoldExpiresAt(null);
        order.setNote("Demo task #29 — giao thất bại, còn HOLD, chưa tạo phiếu nhận về");

        SalesOrderLine line = new SalesOrderLine();
        line.setVariant(variant);
        line.setUnit(SellUnit.PACK);
        line.setQty(QTY_KHAY);
        line.setQtyKhay(QTY_KHAY);
        line.setUnitPrice(unitPrice);
        line.setDiscountPct(BigDecimal.ZERO);
        line.setLineTotal(lineTotal);
        line.setPriceBasis(PriceBasis.LIST);
        line.setOutstandingQty(0);
        order.addLine(line);
        order = salesOrderRepository.saveAndFlush(order);

        SalesOrder held = salesOrderRepository
                .findWithDetailsById(order.getId())
                .orElse(order);
        try {
            int n = orderAllocationService.holdForOrder(held, actor == null ? null : actor.getId(), false);
            held = salesOrderRepository.findWithDetailsById(order.getId()).orElse(held);
            held.setAllocationStatus(AllocationStatus.FULL);
            held.setState(OrderState.TO_SHIP);
            salesOrderRepository.save(held);
            log.info("Demo nhận về {}: đã HOLD {} khay SKU {}", orderCode, n, variant.getSku());
        } catch (ApiException ex) {
            log.warn("Skip demo nhận về {}: hold thất bại — {}", orderCode, ex.getMessage());
            return false;
        }

        SalesOrderLine savedLine = held.getLines().get(0);
        Delivery delivery = new Delivery();
        delivery.setCode(deliveryCode);
        delivery.setOrder(held);
        delivery.setLegLabel("Trọn đơn");
        // Lịch giao theo ngày VN — khớp nút «Hôm nay» trên FE (tránh lệch UTC).
        delivery.setScheduledDate(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")));
        delivery.setTimeWindow("14:00-15:00");
        delivery.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        delivery.setAddress(ship);
        if (ship != null) {
            delivery.setInRadius(ship.isInDeliveryRadius());
            delivery.setDistanceKm(ship.getDistanceKm());
        } else {
            delivery.setInRadius(true);
            delivery.setDistanceKm(new BigDecimal("5.00"));
        }
        delivery.setState(DeliveryState.FAILED);
        delivery.setNote("Demo — giao thất bại, chờ Nhận về");
        delivery.setItems(List.of(new DeliveryItem(savedLine.getId(), QTY_KHAY)));
        delivery = deliveryRepository.save(delivery);

        OrderEvent event = new OrderEvent();
        event.setOrder(held);
        event.setEventType("delivery");
        event.setDescription("Demo: giao thất bại " + delivery.getLegLabel() + " — sẵn sàng Nhận về");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        log.info(
                "Demo nhận về sẵn sàng: đơn {} · đợt {} (id={}) · {} khay {} — Giao hàng → lọc Thất bại → Nhận về",
                orderCode,
                delivery.getCode(),
                delivery.getId(),
                QTY_KHAY,
                variant.getSku());
        return true;
    }
}
