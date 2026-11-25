package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.customer.AddressKind;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.CustomerAddressService;
import com.hbsisms.feature.customer.CustomerCodeGenerator;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.payment.CustomerDebtLedger;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.payment.DebtEntryType;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.payos.PayosLinkCanceller;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.sales.dto.OrderCountsResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.sales.dto.OrderListItemResponse;
import com.hbsisms.feature.sales.dto.OrderReceiptLineResponse;
import com.hbsisms.feature.sales.dto.OrderReceiptResponse;
import com.hbsisms.feature.sales.dto.ShipAddressInput;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales order list/detail/create + lifecycle (Inter 2 Phase 2–3).
 * Confirm gọi {@link OrderAllocationService} giữ hàng FEFO.
 */
@Service
public class SalesOrderService {

    public static final String REF_TYPE = OrderAllocationService.REF_TYPE;

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "placedAt");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final Pattern VN_PHONE = Pattern.compile("^0\\d{9}$");

    private static final Map<OrderState, List<OrderState>> MANUAL_NEXT = new EnumMap<>(OrderState.class);

    static {
        MANUAL_NEXT.put(OrderState.PENDING, List.of(OrderState.CONFIRMED));
        MANUAL_NEXT.put(OrderState.CONFIRMED, List.of(OrderState.PREPARING));
        MANUAL_NEXT.put(OrderState.PREPARING, List.of(OrderState.TO_SHIP));
        MANUAL_NEXT.put(OrderState.DELIVERED, List.of(OrderState.COMPLETED));
    }

    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final DeliveryRepository deliveryRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerAddressService customerAddressService;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final CustomerDebtLedgerRepository customerDebtLedgerRepository;
    private final QuoteRepository quoteRepository;
    private final PricingService pricingService;
    private final AuditLogService auditLogService;
    private final OrderAllocationService orderAllocationService;
    private final PayosLinkCanceller payosLinkCanceller;
    private final BusinessCodeService businessCodeService;
    private final AppConfigService appConfigService;
    private final OrderDetailAssembler orderDetailAssembler;
    private final Clock clock;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            DeliveryRepository deliveryRepository,
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            CustomerAddressRepository customerAddressRepository,
            CustomerAddressService customerAddressService,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            ApprovalTaskRepository approvalTaskRepository,
            CustomerDebtLedgerRepository customerDebtLedgerRepository,
            QuoteRepository quoteRepository,
            PricingService pricingService,
            AuditLogService auditLogService,
            OrderAllocationService orderAllocationService,
            PayosLinkCanceller payosLinkCanceller,
            BusinessCodeService businessCodeService,
            AppConfigService appConfigService,
            OrderDetailAssembler orderDetailAssembler,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.deliveryRepository = deliveryRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.customerAddressService = customerAddressService;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.customerDebtLedgerRepository = customerDebtLedgerRepository;
        this.quoteRepository = quoteRepository;
        this.pricingService = pricingService;
        this.auditLogService = auditLogService;
        this.orderAllocationService = orderAllocationService;
        this.payosLinkCanceller = payosLinkCanceller;
        this.businessCodeService = businessCodeService;
        this.appConfigService = appConfigService;
        this.orderDetailAssembler = orderDetailAssembler;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderListItemResponse> search(
            String q,
            String state,
            String orderType,
            String paymentStatus,
            String channel,
            String allocationStatus,
            Long customerId,
            Long placedBy,
            String fulfillmentMethod,
            String placedFrom,
            String placedTo,
            String deliveryFrom,
            String deliveryTo,
            String timeWindow,
            String group,
            int page,
            int pageSize) {
        return PageResponse.from(salesOrderRepository
                .findAll(
                        OrderSpecifications.search(
                                q,
                                parseState(state),
                                parseOrderType(orderType),
                                parsePaymentStatus(paymentStatus),
                                parseChannel(channel),
                                parseAllocationStatus(allocationStatus),
                                customerId,
                                placedBy,
                                parseFulfillmentMethod(fulfillmentMethod),
                                parseDate(placedFrom),
                                parseDate(placedTo),
                                parseDate(deliveryFrom),
                                parseDate(deliveryTo),
                                timeWindow,
                                parseGroup(group)),
                        PageRequests.of(page, pageSize, LIST_SORT))
                .map(OrderListItemResponse::from));
    }

    /** Inter 3 dòng 5 — đếm theo 9 tab; cùng bộ lọc list trừ state/group/page. */
    @Transactional(readOnly = true)
    public OrderCountsResponse countGroups(
            String q,
            String orderType,
            String paymentStatus,
            String channel,
            String allocationStatus,
            Long customerId,
            Long placedBy,
            String fulfillmentMethod,
            String placedFrom,
            String placedTo,
            String deliveryFrom,
            String deliveryTo,
            String timeWindow) {
        var shared = OrderSpecifications.sharedFilters(
                q,
                parseOrderType(orderType),
                parsePaymentStatus(paymentStatus),
                parseChannel(channel),
                parseAllocationStatus(allocationStatus),
                customerId,
                placedBy,
                parseFulfillmentMethod(fulfillmentMethod),
                parseDate(placedFrom),
                parseDate(placedTo),
                parseDate(deliveryFrom),
                parseDate(deliveryTo),
                timeWindow);
        return new OrderCountsResponse(
                countGroup(shared, OrderListGroup.AWAITING_PAYMENT),
                countGroup(shared, OrderListGroup.AWAITING_PREPARE),
                countGroup(shared, OrderListGroup.PREPARING),
                countGroup(shared, OrderListGroup.READY),
                countGroup(shared, OrderListGroup.DELIVERING),
                countGroup(shared, OrderListGroup.DELIVERY_FAILED),
                countGroup(shared, OrderListGroup.NEEDS_REFUND),
                countGroup(shared, OrderListGroup.COMPLETED),
                countGroup(shared, OrderListGroup.CANCELLED));
    }

    private long countGroup(Specification<SalesOrder> shared, OrderListGroup group) {
        return salesOrderRepository.count(Specification.allOf(shared, OrderSpecifications.groupSpec(group)));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long id) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        return orderDetailAssembler.assemble(order);
    }

    @Transactional
    public OrderDetailResponse create(OrderInputRequest request, Long actorId) {
        return create(request, actorId, false);
    }

    /**
     * @param allowOutstandingShortage {@code true} khi tạo từ yêu cầu đã khả thi — cho phép
     *     ghi {@code outstandingQty}; đơn Sales thường phải giữ đủ hàng.
     */
    @Transactional
    public OrderDetailResponse create(
            OrderInputRequest request, Long actorId, boolean allowOutstandingShortage) {
        rejectCreditNet(request.paymentMethod());
        if (request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Đơn phải có ít nhất một dòng hàng");
        }

        PaymentMethod paymentMethod =
                CollectOnDelivery.normalize(request.paymentMethod(), request.fulfillmentMethod());

        Customer customer = resolveCustomerForOrder(request);
        String customerName = resolveCustomerName(customer, request.customerName());
        CustomerAddress shipAddress = resolveShipAddress(customer, request);

        AppUser placedBy = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        PricingResultResponse priced = pricingService.resolve(new PricingRequest(
                customer.getId(),
                paymentMethod.code(),
                moneyOrZero(request.shippingFee()),
                moneyOrZero(request.discountTotal()),
                request.lines()));

        // Sales nội bộ: thiếu tồn → không tạo đơn / không HOLD.
        // Từ yêu cầu đã khả thi (allowOutstandingShortage): bỏ assert — hold phần có + outstanding phần thiếu.
        if (!allowOutstandingShortage) {
            assertSufficientStock(priced);
        }

        Instant now = Instant.now(clock);
        SalesOrder order = new SalesOrder();
        BigDecimal discountTotal = moneyOrZero(request.discountTotal());
        if (discountTotal.compareTo(priced.subtotal()) > 0) {
            throw ApiException.badRequest("Giảm giá không được lớn hơn tạm tính");
        }

        order.setCode(businessCodeService.nextSalesOrderCode());
        order.setCustomer(customer);
        order.setCustomerName(customerName);
        order.setOrderType(request.orderType());
        order.setChannel(request.channel());
        order.setPaymentMethod(paymentMethod);
        order.setPaymentTermDays((short) 0);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(request.fulfillmentMethod());
        order.setShipAddress(shipAddress);
        order.setState(OrderState.PENDING);
        order.setSubtotal(priced.subtotal());
        order.setDiscountTotal(discountTotal);
        order.setShippingFee(moneyOrZero(request.shippingFee()));
        order.setTotal(priced.total());
        order.setVoucherCode(Strings.trimToNull(request.voucherCode()));
        // Không bán nợ → không còn vượt hạn mức công nợ trên đơn Sales mới.
        order.setOverLimit(false);
        order.setOverLimitAmount(ZERO);
        order.setPlacedBy(placedBy);
        order.setPlacedAt(now);
        order.setNote(Strings.trimToNull(request.note()));

        for (PricedLineResponse line : priced.lines()) {
            ProductVariant variant = productVariantRepository
                    .findById(line.variantId())
                    .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));
            SalesOrderLine row = new SalesOrderLine();
            row.setVariant(variant);
            row.setUnit(line.unit());
            row.setQty(line.qty());
            row.setQtyKhay(line.qtyKhay());
            row.setUnitPrice(line.unitPrice());
            row.setDiscountPct(ZERO);
            row.setLineTotal(line.lineTotal());
            row.setPriceBasis(line.priceBasis());
            row.setOutstandingQty(0);
            order.addLine(row);
        }

        salesOrderRepository.save(order);
        addEvent(order, "created", "Tạo đơn", placedBy);

        // Giảm giá / giá tay vượt ngưỡng → chờ Chủ duyệt, chưa hold / chưa PayOS.
        if (!allowOutstandingShortage && requiresPriceApproval(priced, discountTotal)) {
            createPriceOverrideApproval(order, placedBy, priced, discountTotal);
            Map<String, Object> auditDetail = new HashMap<>();
            auditDetail.put("total", order.getTotal());
            auditDetail.put("awaitingPriceApproval", true);
            auditLogService.record(actorId, "create", "sales_order", order.getId(), auditDetail);
            return getDetail(order.getId());
        }

        confirmOrder(order, placedBy, allowOutstandingShortage);

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("total", order.getTotal());
        auditLogService.record(actorId, "create", "sales_order", order.getId(), auditDetail);

        return getDetail(order.getId());
    }

    /**
     * Chủ duyệt/từ chối vượt hạn mức hoặc giảm giá / giá tay — gọi từ {@link com.hbsisms.feature.approval.ApprovalService}.
     */
    @Transactional
    public void applyOverLimitDecision(Long orderId, boolean approved, AppUser actor, String note) {
        applyOrderApprovalDecision(orderId, approved, actor, note, ApprovalType.OVER_LIMIT_ORDER);
    }

    /** Duyệt giảm giá / sửa giá tay trên đơn. */
    @Transactional
    public void applyPriceOverrideDecision(Long orderId, boolean approved, AppUser actor, String note) {
        applyOrderApprovalDecision(orderId, approved, actor, note, ApprovalType.ORDER_PRICE_OVERRIDE);
    }

    private void applyOrderApprovalDecision(
            Long orderId, boolean approved, AppUser actor, String note, ApprovalType expectedType) {
        salesOrderRepository.findByIdForUpdate(orderId);
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        if (order.getState() == OrderState.CANCELLED || order.getState() == OrderState.EXPIRED) {
            throw ApiException.conflict("Đơn đã hủy hoặc hết hạn");
        }
        if (order.getApprovedBy() != null || order.getState() != OrderState.PENDING) {
            throw ApiException.conflict("Đơn không còn chờ duyệt");
        }
        // Chỉ admin/owner (quyền approve) gọi được; được tự duyệt đơn do chính mình tạo.

        String label = expectedType == ApprovalType.ORDER_PRICE_OVERRIDE
                ? "giảm giá / giá tay"
                : "vượt hạn mức";
        String suffix = Strings.trimToNull(note) == null ? "" : ": " + note.trim();
        if (approved) {
            order.setApprovedBy(actor);
            addEvent(order, "approved", "Chủ cửa hàng duyệt " + label + suffix, actor);
            confirmOrder(order, actor, false);
        } else {
            cancelOrderInternal(order, "Từ chối duyệt " + label + suffix, actor, false);
        }
    }

    /**
     * Sửa đơn trước khi chuẩn bị: chỉ {@link OrderState#PENDING} / {@link OrderState#CONFIRMED}.
     * Đơn đã CONFIRMED: nhả hold → thay dòng → giữ lại FEFO.
     */
    @Transactional
    public OrderDetailResponse update(Long orderId, OrderInputRequest request, Long actorId) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        OrderState state = order.getState();
        if (state != OrderState.PENDING && state != OrderState.CONFIRMED) {
            throw ApiException.conflict("Chỉ sửa được đơn ở trạng thái Chờ xử lý hoặc Đã xác nhận");
        }
        PaymentStatus pay = order.getPaymentStatus();
        if (pay == PaymentStatus.PAID || pay == PaymentStatus.PARTIAL) {
            throw ApiException.conflict("Đơn đã thu tiền, không thể sửa");
        }

        rejectCreditNet(request.paymentMethod());
        if (request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Đơn phải có ít nhất một dòng hàng");
        }

        PaymentMethod paymentMethod =
                CollectOnDelivery.normalize(request.paymentMethod(), request.fulfillmentMethod());

        Customer customer = resolveCustomerForOrder(request);
        String customerName = resolveCustomerName(customer, request.customerName());
        CustomerAddress shipAddress = resolveShipAddress(customer, request);

        AppUser actor = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        boolean wasConfirmed = state == OrderState.CONFIRMED;
        boolean wasDebt = order.getPaymentStatus() == PaymentStatus.DEBT;

        if (wasConfirmed) {
            orderAllocationService.releaseForOrder(order, actorId, "Sửa đơn — giải phóng hold cũ");
            salesOrderRepository.saveAndFlush(order);
        }
        if (wasDebt && order.getCustomer() != null) {
            reverseDebtCharge(order, "Sửa đơn " + order.getCode() + " — đảo nợ cũ");
            order.setPaymentStatus(PaymentStatus.UNPAID);
        }

        PricingResultResponse priced = pricingService.resolve(new PricingRequest(
                customer.getId(),
                paymentMethod.code(),
                moneyOrZero(request.shippingFee()),
                moneyOrZero(request.discountTotal()),
                request.lines()));

        order.clearLines();
        salesOrderRepository.saveAndFlush(order);

        // Load lại sau khi orphan-delete dòng cũ — tránh EntityNotFound trên flush kế tiếp.
        order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));

        order.setCustomer(customer);
        order.setCustomerName(customerName);
        order.setOrderType(request.orderType());
        order.setChannel(request.channel());
        order.setPaymentMethod(paymentMethod);
        order.setPaymentTermDays((short) 0);
        order.setFulfillmentMethod(request.fulfillmentMethod());
        order.setShipAddress(shipAddress);
        order.setSubtotal(priced.subtotal());
        order.setDiscountTotal(moneyOrZero(request.discountTotal()));
        order.setShippingFee(moneyOrZero(request.shippingFee()));
        order.setTotal(priced.total());
        order.setVoucherCode(Strings.trimToNull(request.voucherCode()));
        order.setOverLimit(false);
        order.setOverLimitAmount(ZERO);
        order.setNote(Strings.trimToNull(request.note()));
        order.setHoldExpiresAt(null);
        order.setAllocationStatus(AllocationStatus.NONE);

        for (PricedLineResponse line : priced.lines()) {
            ProductVariant variant = productVariantRepository
                    .findById(line.variantId())
                    .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));
            SalesOrderLine row = new SalesOrderLine();
            row.setVariant(variant);
            row.setUnit(line.unit());
            row.setQty(line.qty());
            row.setQtyKhay(line.qtyKhay());
            row.setUnitPrice(line.unitPrice());
            row.setDiscountPct(ZERO);
            row.setLineTotal(line.lineTotal());
            row.setPriceBasis(line.priceBasis());
            row.setOutstandingQty(0);
            order.addLine(row);
        }
        salesOrderRepository.saveAndFlush(order);

        final SalesOrder current = order;
        if (wasConfirmed) {
            int held = orderAllocationService.holdForOrder(current, actorId, false);
            addEvent(
                    current,
                    "updated",
                    "Sửa đơn — giữ lại " + held + " khay (" + current.getAllocationStatus().code() + ")",
                    actor);
            salesOrderRepository.save(current);
        } else {
            approvalTaskRepository
                    .findByRefTypeAndRefIdAndStatus(REF_TYPE, current.getId(), ApprovalStatus.PENDING)
                    .ifPresent(task -> {
                        task.setStatus(ApprovalStatus.APPROVED);
                        task.setNote("Đơn sau khi sửa — không còn đường bán nợ/vượt hạn mức");
                        task.setDecidedAt(Instant.now(clock));
                        task.setDecidedBy(actor);
                        approvalTaskRepository.save(task);
                    });
            addEvent(current, "updated", "Sửa đơn", actor);
            confirmOrder(current, actor, false);
        }

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("total", current.getTotal());
        auditLogService.record(actorId, "update", REF_TYPE, current.getId(), auditDetail);

        return getDetail(current.getId());
    }

    /**
     * Snapshot biên lai bán hàng (không phải hóa đơn điện tử).
     * Chỉ khi đã thanh toán đủ ({@code paid}). Mỗi lần gọi ghi audit {@code print_receipt}.
     */
    @Transactional
    public OrderReceiptResponse receipt(Long orderId, Long actorId) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        PaymentStatus pay = order.getPaymentStatus();
        if (pay != PaymentStatus.PAID) {
            throw ApiException.conflict("Chỉ in biên lai khi đã thanh toán đủ");
        }

        List<OrderReceiptLineResponse> lines = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            var variant = line.getVariant();
            var product = variant.getProduct();
            String unitLabel = line.getUnit() == SellUnit.CASE ? "thùng" : "khay";
            lines.add(new OrderReceiptLineResponse(
                    variant.getId(),
                    product.getName(),
                    variant.getPackLabel(),
                    line.getQty() + " " + unitLabel,
                    line.getUnitPrice(),
                    line.getLineTotal()));
        }

        String paymentMethod = switch (order.getPaymentMethod()) {
            case CASH -> "cash";
            case CREDIT_NET -> "credit_net";
            default -> "bank_transfer";
        };

        BigDecimal debtAfter = null;
        if (order.getPaymentMethod() == PaymentMethod.CREDIT_NET && order.getCustomer() != null) {
            debtAfter = order.getCustomer().getCurrentDebt();
        }

        String cashierName = order.getPlacedBy() == null ? "—" : order.getPlacedBy().getFullName();
        String customerName = Strings.trimToNull(order.getCustomerName()) != null
                ? order.getCustomerName().trim()
                : (order.getCustomer() == null ? "Khách vãng lai" : order.getCustomer().getName());

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("code", order.getCode());
        auditLogService.record(actorId, "print_receipt", REF_TYPE, order.getId(), auditDetail);

        return new OrderReceiptResponse(
                order.getId(),
                order.getCode(),
                order.getPlacedAt(),
                customerName,
                cashierName,
                paymentMethod,
                lines,
                order.getSubtotal(),
                order.getDiscountTotal(),
                order.getTotal(),
                null,
                null,
                debtAfter);
    }

    @Transactional
    public void transition(Long orderId, OrderState target, Long actorId) {
        // Khóa hàng đơn (FOR UPDATE) trong cùng transaction để tránh đổi trạng thái đồng thời.
        salesOrderRepository.findByIdForUpdate(orderId);
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        AppUser actor = requireActor(actorId);

        if (order.isOverLimit() && order.getApprovedBy() == null) {
            throw ApiException.conflict("Đơn vượt hạn mức, cần Chủ cửa hàng duyệt trước");
        }
        List<OrderState> allowed = MANUAL_NEXT.getOrDefault(order.getState(), List.of());
        if (!allowed.contains(target)) {
            throw ApiException.conflict("Không thể chuyển sang trạng thái này");
        }

        if (target == OrderState.CONFIRMED) {
            confirmOrder(order, actor, false);
            return;
        }
        if (target == OrderState.PREPARING) {
            assertReadyForPreparing(order);
        }

        order.setState(target);
        String label = switch (target) {
            case PREPARING -> "Bắt đầu chuẩn bị hàng";
            case TO_SHIP -> "Hàng sẵn sàng, chờ giao";
            case COMPLETED -> "Hoàn tất đơn";
            default -> target.code();
        };
        addEvent(order, target.code(), label, actor);
        salesOrderRepository.save(order);
    }

    @Transactional
    public void cancel(Long orderId, String reason, Long actorId) {
        String why = Strings.trimToNull(reason);
        if (why == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do hủy");
        }
        // Khóa hàng đơn (FOR UPDATE) trong cùng transaction để tránh hủy/đổi trạng thái đồng thời.
        salesOrderRepository.findByIdForUpdate(orderId);
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        AppUser actor = requireActor(actorId);
        cancelOrderInternal(order, why, actor, true);
        Map<String, Object> detail = new HashMap<>();
        detail.put("reason", why);
        detail.put("paymentStatus", order.getPaymentStatus().code());
        auditLogService.record(actorId, "cancel", REF_TYPE, order.getId(), detail);
    }

    /** Xác nhận đơn + giữ hàng FEFO. Không bán nợ — không ghi DEBT. */
    private void confirmOrder(SalesOrder order, AppUser actor, boolean allowOutstandingShortage) {
        order.setState(OrderState.CONFIRMED);

        Long actorId = actor == null ? null : actor.getId();
        int held = orderAllocationService.holdForOrder(order, actorId, allowOutstandingShortage);
        // holdForOrder đã save + Stock clearAutomatically detach entity → không save lại bản stale.
        SalesOrder fresh = salesOrderRepository
                .findWithDetailsById(order.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn"));
        order.setState(fresh.getState());
        order.setAllocationStatus(fresh.getAllocationStatus());
        order.setHoldExpiresAt(fresh.getHoldExpiresAt());
        addEvent(
                fresh,
                "confirmed",
                "Xác nhận đơn — giữ " + held + " khay (FEFO, " + fresh.getAllocationStatus().code() + ")",
                actor);
        salesOrderRepository.save(fresh);
    }

    private void cancelOrderInternal(SalesOrder order, String reason, AppUser actor, boolean rejectPendingApproval) {
        OrderState state = order.getState();
        if (state == OrderState.DELIVERED
                || state == OrderState.COMPLETED
                || state == OrderState.CANCELLED
                || state == OrderState.EXPIRED
                || state == OrderState.PARTIAL
                || state == OrderState.PREPARING
                || state == OrderState.TO_SHIP) {
            throw ApiException.conflict(
                    "Đơn đã xuất kho, đang giao hoặc đã kết thúc — không hủy thẳng, dùng phiếu trả hàng (RET)");
        }

        boolean wasDebt = order.getPaymentStatus() == PaymentStatus.DEBT;
        boolean hadPaid = order.getPaymentStatus() == PaymentStatus.PAID
                || order.getPaymentStatus() == PaymentStatus.PARTIAL;

        Long actorId = actor == null ? null : actor.getId();
        orderAllocationService.releaseForOrder(order, actorId, "Huỷ đơn — giải phóng hold");

        if (wasDebt && order.getCustomer() != null) {
            reverseDebtCharge(order, "Hủy đơn " + order.getCode());
        }

        order.setState(OrderState.CANCELLED);
        if (hadPaid) {
            order.setPaymentStatus(PaymentStatus.REFUND_PENDING);
            markPaymentsNeedRefund(order);
        } else {
            order.setPaymentStatus(PaymentStatus.UNPAID);
        }
        order.setHoldExpiresAt(null);

        if (rejectPendingApproval) {
            approvalTaskRepository
                    .findByRefTypeAndRefIdAndStatus(REF_TYPE, order.getId(), ApprovalStatus.PENDING)
                    .ifPresent(task -> {
                        task.setStatus(ApprovalStatus.REJECTED);
                        task.setNote("Đơn đã hủy");
                        task.setDecidedAt(Instant.now(clock));
                        task.setDecidedBy(actor);
                        approvalTaskRepository.save(task);
                    });
        }

        // Khóa QR PayOS còn chờ — khách không trả tiền được cho đơn đã hủy
        payosLinkCanceller.cancelPendingLinks(order, "Cửa hàng hủy đơn");

        for (Delivery delivery : deliveryRepository.findByOrder_IdOrderByIdAsc(order.getId())) {
            if (delivery.getState() != DeliveryState.DELIVERED) {
                delivery.setState(DeliveryState.FAILED);
            }
        }

        String eventDesc = hadPaid
                ? "Hủy đơn (đã thu tiền — chờ hoàn tiền): " + reason
                : "Hủy đơn: " + reason;
        addEvent(order, "cancelled", eventDesc, actor);
        salesOrderRepository.save(order);
    }

    private void assertReadyForPreparing(SalesOrder order) {
        // COD: thu khi giao — cho chuẩn bị khi đã giữ đủ hàng, chưa cần paid.
        if (!CollectOnDelivery.matches(order) && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.conflict("Chỉ chuẩn bị khi đã thanh toán đủ");
        }
        if (order.getAllocationStatus() != AllocationStatus.FULL) {
            throw ApiException.conflict("Chưa phân bổ đủ hàng — không thể chuẩn bị");
        }
    }

    /**
     * State machine bước kế tiếp cho FE. {@link OrderState#PREPARING}: prepaid cần
     * {@code PAID}+{@code FULL}; COD chỉ cần {@code FULL}.
     * Package-private: {@link OrderDetailAssembler} tái sử dụng.
     */
    static List<OrderState> allowedNextStates(SalesOrder order) {
        if (order.isOverLimit() && order.getApprovedBy() == null) {
            return List.of();
        }
        List<OrderState> raw = MANUAL_NEXT.getOrDefault(order.getState(), List.of());
        if (raw.isEmpty()) {
            return List.of();
        }
        List<OrderState> next = new ArrayList<>(raw.size());
        for (OrderState state : raw) {
            if (state == OrderState.PREPARING) {
                boolean allocationOk = order.getAllocationStatus() == AllocationStatus.FULL;
                boolean paymentOk = CollectOnDelivery.matches(order)
                        || order.getPaymentStatus() == PaymentStatus.PAID;
                if (allocationOk && paymentOk) {
                    next.add(state);
                }
            } else {
                next.add(state);
            }
        }
        return List.copyOf(next);
    }

    private void rejectCreditNet(PaymentMethod method) {
        if (method == PaymentMethod.CREDIT_NET) {
            throw ApiException.conflict("Không bán nợ — mọi đơn phải thanh toán đủ trước khi chuẩn bị");
        }
    }

    private Customer resolveCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn khách hàng"));
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể tạo đơn");
        }
        return customer;
    }

    /**
     * Khách có sẵn theo {@code customerId}, hoặc find-or-create B2C theo SĐT (nhập nhanh vãng lai).
     * Đơn staff sau bước này luôn có {@code customer} — không còn {@code customer_id} null.
     */
    private Customer resolveCustomerForOrder(OrderInputRequest request) {
        if (request.customerId() != null) {
            return resolveCustomer(request.customerId());
        }

        String phone = normalizeRequiredPhone(request.phone());
        String name = Strings.trimToNull(request.customerName());
        if (name == null) {
            name = "Khách vãng lai";
        }

        Customer existing = customerRepository
                .findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(phone)
                .or(() -> customerRepository.findFirstByPhoneOrderByIdAsc(phone))
                .orElse(null);
        if (existing != null) {
            if (!existing.isActive()) {
                throw ApiException.conflict("Khách hàng đã bị khóa, không thể tạo đơn");
            }
            if (existing.getAccountUser() == null && !name.equals(existing.getName())) {
                existing.setName(name);
                return customerRepository.save(existing);
            }
            return existing;
        }

        Customer created = new Customer();
        created.setCode(nextB2cCustomerCode());
        created.setCustomerType(CustomerType.B2C);
        created.setName(name);
        created.setPhone(phone);
        created.setAccountUser(null);
        created.setCreditLimit(BigDecimal.ZERO);
        created.setNetDays((short) 0);
        created.setCurrentDebt(BigDecimal.ZERO);
        created.setActive(true);
        return customerRepository.save(created);
    }

    private CustomerAddress resolveShipAddress(Customer customer, OrderInputRequest request) {
        boolean needsAddress = request.fulfillmentMethod() != FulfillmentMethod.PICKUP;

        if (request.shipAddressId() != null) {
            return customerAddressRepository
                    .findByIdAndCustomerId(request.shipAddressId(), customer.getId())
                    .orElseThrow(() -> ApiException.badRequest("Địa chỉ giao hàng không hợp lệ"));
        }

        ShipAddressInput input = request.shipAddress();
        if (input != null) {
            String line1 = Strings.trimToNull(input.line1());
            if (line1 == null) {
                throw ApiException.badRequest("Địa chỉ là bắt buộc");
            }
            String recipient = Strings.trimToNull(input.recipientName());
            if (recipient == null) {
                recipient = customer.getName();
            }
            String phone = Strings.trimToNull(input.phone());
            if (phone == null) {
                phone = customer.getPhone();
            }
            String province = Strings.trimToNull(input.province());
            AddressResponse created = customerAddressService.create(
                    customer.getId(),
                    new AddressRequest(
                            AddressKind.SHIPPING,
                            recipient,
                            phone,
                            line1,
                            Strings.trimToNull(input.ward()),
                            Strings.trimToNull(input.district()),
                            province,
                            input.latitude(),
                            input.longitude(),
                            input.distanceKm(),
                            Boolean.TRUE));
            return customerAddressRepository
                    .findByIdAndCustomerId(created.id(), customer.getId())
                    .orElseThrow(() -> ApiException.badRequest("Địa chỉ giao hàng không hợp lệ"));
        }

        if (needsAddress) {
            throw ApiException.badRequest("Vui lòng nhập địa chỉ giao hàng");
        }
        return null;
    }

    private String nextB2cCustomerCode() {
        String prefix = CustomerCodeGenerator.prefixFor(CustomerType.B2C);
        return CustomerCodeGenerator.nextCode(CustomerType.B2C, customerRepository.findCodesByPrefix(prefix));
    }

    private static String normalizeRequiredPhone(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            throw ApiException.badRequest("Số điện thoại khách là bắt buộc");
        }
        String digits = value.replaceAll("[\\s.-]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!VN_PHONE.matcher(digits).matches()) {
            throw ApiException.badRequest("Số điện thoại không hợp lệ");
        }
        return digits;
    }

    private static String resolveCustomerName(Customer customer, String requestedName) {
        if (customer != null) {
            return customer.getName();
        }
        String walkIn = Strings.trimToNull(requestedName);
        return walkIn != null ? walkIn : "Khách vãng lai";
    }

    private AppUser requireActor(Long actorId) {
        if (actorId == null) {
            throw ApiException.forbidden("Cần đăng nhập");
        }
        return userRepository
                .findById(actorId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    /** Public hook: tạo đơn từ yêu cầu khả thi — cho phép outstanding_qty phần thiếu. */
    @Transactional
    public OrderDetailResponse createFromRequest(OrderInputRequest request, Long actorId) {
        return create(request, actorId, true);
    }

    /**
     * Chuyển báo giá đã chốt → đơn: giữ snapshot giá báo giá ({@link PriceBasis#QUOTE}).
     * Kiểm tra tồn như tạo đơn nội bộ; credit check theo phương thức thanh toán mặc định.
     */
    @Transactional
    public OrderDetailResponse createFromQuote(Long quoteId, Long actorId) {
        Quote quote = quoteRepository
                .findWithDetailsById(quoteId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy báo giá"));
        if (quote.getStatus() != QuoteStatus.APPROVED) {
            throw ApiException.conflict("Chỉ báo giá đã chốt mới chuyển được thành đơn");
        }
        if (salesOrderRepository.findFirstBySourceQuote_Id(quote.getId()).isPresent()) {
            throw ApiException.conflict("Báo giá này đã có đơn hàng");
        }

        Customer customer = quote.getCustomer();
        if (customer == null) {
            throw ApiException.badRequest("Báo giá thiếu khách hàng");
        }
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể tạo đơn");
        }
        if (quote.getLines().isEmpty()) {
            throw ApiException.badRequest("Báo giá không có dòng hàng");
        }

        // Không bán nợ: convert báo giá dùng chuyển khoản, không CREDIT_NET.
        PaymentMethod paymentMethod = PaymentMethod.BANK_TRANSFER;
        OrderType orderType = OrderType.RETAIL;

        CustomerAddress shipAddress = customerAddressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(customer.getId())
                .stream()
                .findFirst()
                .orElse(null);
        FulfillmentMethod fulfillment = shipAddress == null
                ? FulfillmentMethod.PICKUP
                : (shipAddress.isInDeliveryRadius()
                        ? FulfillmentMethod.SHOP_DELIVERY
                        : FulfillmentMethod.PARTNER_DELIVERY);

        List<PricingLineInput> pricingLines = quote.getLines().stream()
                .map(line -> new PricingLineInput(
                        line.getVariant().getId(), line.getUnit(), line.getQty(), line.getUnitPrice()))
                .toList();
        PricingResultResponse priced = pricingService.resolve(new PricingRequest(
                customer.getId(), paymentMethod.code(), ZERO, ZERO, pricingLines));
        assertSufficientStock(priced);

        AppUser placedBy = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        Instant now = Instant.now(clock);
        SalesOrder order = new SalesOrder();
        order.setCode(businessCodeService.nextSalesOrderCode());
        order.setCustomer(customer);
        order.setCustomerName(customer.getName());
        order.setOrderType(orderType);
        order.setChannel(SaleChannel.PHONE);
        order.setSourceQuote(quote);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentTermDays((short) 0);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(fulfillment);
        order.setShipAddress(shipAddress);
        order.setState(OrderState.PENDING);
        order.setSubtotal(quote.getSubtotal());
        order.setDiscountTotal(ZERO);
        order.setShippingFee(ZERO);
        order.setTotal(quote.getTotal());
        order.setOverLimit(false);
        order.setOverLimitAmount(ZERO);
        order.setPlacedBy(placedBy);
        order.setPlacedAt(now);
        order.setNote("Từ báo giá " + quote.getCode());

        for (QuoteLine line : quote.getLines()) {
            SalesOrderLine row = new SalesOrderLine();
            row.setVariant(line.getVariant());
            row.setUnit(line.getUnit());
            row.setQty(line.getQty());
            row.setQtyKhay(line.getQtyKhay());
            row.setUnitPrice(line.getUnitPrice());
            row.setDiscountPct(ZERO);
            row.setLineTotal(line.getLineTotal());
            row.setPriceBasis(PriceBasis.QUOTE);
            row.setOutstandingQty(0);
            order.addLine(row);
        }

        salesOrderRepository.save(order);
        addEvent(order, "created", "Tạo đơn từ báo giá " + quote.getCode(), placedBy);

        SalesRequest linked = quote.getRequest();
        if (linked != null
                && linked.getStatus() != RequestStatus.REJECTED
                && linked.getStatus() != RequestStatus.CLOSED) {
            linked.setStatus(RequestStatus.ORDERED);
            linked.setOrderCode(order.getCode());
        }

        if (order.isOverLimit()) {
            createOverLimitApproval(order, customer, placedBy);
            addEvent(order, "approval", "Đơn vượt hạn mức, chờ Chủ cửa hàng duyệt", placedBy);
        } else {
            confirmOrder(order, placedBy, false);
        }

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("total", order.getTotal());
        auditDetail.put("sourceQuoteId", quote.getId());
        auditLogService.record(actorId, "create", "sales_order", order.getId(), auditDetail);

        return getDetail(order.getId());
    }

    /** Hủy đơn: RELEASE hold nếu còn. */
    @Transactional
    public void releaseHoldsOnCancel(SalesOrder order, Long actorId) {
        orderAllocationService.releaseForOrder(order, actorId, "Huỷ đơn — giải phóng hold");
    }

    private void chargeDebt(SalesOrder order) {
        Customer customer = order.getCustomer();
        BigDecimal amount = order.getTotal();
        customer.setCurrentDebt(customer.getCurrentDebt().add(amount));

        CustomerDebtLedger entry = new CustomerDebtLedger();
        entry.setCustomer(customer);
        entry.setOrder(order);
        entry.setEntryType(DebtEntryType.CHARGE);
        entry.setAmount(amount);
        entry.setBalanceAfter(customer.getCurrentDebt());
        entry.setDueDate(LocalDate.now(clock).plusDays(order.getPaymentTermDays()));
        entry.setNote("Đơn " + order.getCode());
        customerDebtLedgerRepository.save(entry);
    }

    private void reverseDebtCharge(SalesOrder order, String note) {
        Customer customer = order.getCustomer();
        BigDecimal amount = order.getTotal().negate();
        customer.setCurrentDebt(customer.getCurrentDebt().add(amount));

        CustomerDebtLedger entry = new CustomerDebtLedger();
        entry.setCustomer(customer);
        entry.setOrder(order);
        entry.setEntryType(DebtEntryType.ADJUST);
        entry.setAmount(amount);
        entry.setBalanceAfter(customer.getCurrentDebt());
        entry.setNote(note);
        customerDebtLedgerRepository.save(entry);
    }

    private void createOverLimitApproval(SalesOrder order, Customer customer, AppUser requestedBy) {
        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.OVER_LIMIT_ORDER);
        task.setRefType(REF_TYPE);
        task.setRefId(order.getId());
        task.setTitle(String.format(
                Locale.ROOT,
                "Đơn %s của %s vượt hạn mức %s ₫",
                order.getCode(),
                customer.getName(),
                order.getOverLimitAmount().toPlainString()));
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requestedBy);
        approvalTaskRepository.save(task);
    }

    private void addEvent(SalesOrder order, String eventType, String description, AppUser actor) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(eventType);
        event.setDescription(description);
        event.setCreatedBy(actor);
        orderEventRepository.save(event);
    }

    private boolean requiresPriceApproval(PricingResultResponse priced, BigDecimal discountTotal) {
        BigDecimal subtotal = priced.subtotal() == null ? ZERO : priced.subtotal();
        if (subtotal.compareTo(ZERO) > 0 && discountTotal.compareTo(ZERO) > 0) {
            BigDecimal pct = discountTotal
                    .multiply(BigDecimal.valueOf(100))
                    .divide(subtotal, 2, RoundingMode.HALF_UP);
            if (pct.compareTo(BigDecimal.valueOf(appConfigService.orderDiscountApprovePct())) > 0) {
                return true;
            }
        }
        return priced.lines().stream().anyMatch(line -> line.priceBasis() == PriceBasis.MANUAL);
    }

    private void createPriceOverrideApproval(
            SalesOrder order, AppUser requestedBy, PricingResultResponse priced, BigDecimal discountTotal) {
        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.ORDER_PRICE_OVERRIDE);
        task.setRefType(REF_TYPE);
        task.setRefId(order.getId());
        task.setTitle("Duyệt giảm giá / giá tay đơn " + order.getCode());
        task.setStatus(ApprovalStatus.PENDING);
        task.setRequestedBy(requestedBy);
        task.setNote("discount="
                + discountTotal
                + "; subtotal="
                + priced.subtotal()
                + "; ngưỡng%="
                + appConfigService.orderDiscountApprovePct());
        approvalTaskRepository.save(task);
        addEvent(order, "awaiting_price_approval", "Chờ Chủ duyệt giảm giá / giá tay", requestedBy);
        salesOrderRepository.save(order);
    }

    private void markPaymentsNeedRefund(SalesOrder order) {
        for (Payment payment : paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId())) {
            if (payment.getDirection() != PayDirection.IN || payment.getStatus() != PayTxnStatus.SUCCESS) {
                continue;
            }
            Map<String, Object> raw = payment.getRawPayload() == null
                    ? new HashMap<>()
                    : new HashMap<>(payment.getRawPayload());
            raw.put("needsRefund", true);
            payment.setRawPayload(raw);
            paymentRepository.save(payment);
        }
    }

    private static OrderState parseState(String raw) {
        try {
            return OrderState.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static OrderType parseOrderType(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return OrderType.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static PaymentStatus parsePaymentStatus(String raw) {
        try {
            return PaymentStatus.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static SaleChannel parseChannel(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return SaleChannel.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static AllocationStatus parseAllocationStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return AllocationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Trạng thái phân bổ không hợp lệ: " + raw);
        }
    }

    private static FulfillmentMethod parseFulfillmentMethod(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return FulfillmentMethod.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static OrderListGroup parseGroup(String raw) {
        try {
            return OrderListGroup.fromCode(Strings.trimToNull(raw));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static LocalDate parseDate(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            throw ApiException.badRequest("Ngày không hợp lệ: " + raw);
        }
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Chặn tạo đơn Sales nội bộ khi bất kỳ variant nào vượt tồn khả dụng (gộp theo variant).
     */
    private void assertSufficientStock(PricingResultResponse priced) {
        Map<Long, Integer> neededByVariant = new LinkedHashMap<>();
        Map<Long, Long> availableByVariant = new LinkedHashMap<>();
        for (PricedLineResponse line : priced.lines()) {
            neededByVariant.merge(line.variantId(), line.qtyKhay(), Integer::sum);
            availableByVariant.putIfAbsent(line.variantId(), line.available());
        }

        List<String> shortages = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : neededByVariant.entrySet()) {
            long available = availableByVariant.getOrDefault(entry.getKey(), 0L);
            if (entry.getValue() <= available) {
                continue;
            }
            ProductVariant variant = productVariantRepository.findById(entry.getKey()).orElse(null);
            String label = variant == null
                    ? ("#" + entry.getKey())
                    : variant.getProduct().getName() + " · " + variant.getPackLabel();
            shortages.add(label + " chỉ còn " + available + " khay (cần " + entry.getValue() + ")");
        }
        if (!shortages.isEmpty()) {
            throw ApiException.conflict("Một số sản phẩm không đủ hàng: " + String.join("; ", shortages));
        }
    }
}
