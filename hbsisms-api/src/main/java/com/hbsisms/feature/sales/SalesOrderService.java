package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
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
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.sales.dto.OrderCustomerResponse;
import com.hbsisms.feature.sales.dto.OrderDeliveryViewResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderEventViewResponse;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.sales.dto.OrderLineViewResponse;
import com.hbsisms.feature.sales.dto.OrderListItemResponse;
import com.hbsisms.feature.sales.dto.PaymentViewResponse;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sales order list/detail/create (Inter 2 Phase 2).
 * Confirm gọi {@link OrderAllocationService} giữ hàng FEFO.
 */
@Service
public class SalesOrderService {

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "placedAt");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

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
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final CustomerDebtLedgerRepository customerDebtLedgerRepository;
    private final PricingService pricingService;
    private final AuditLogService auditLogService;
    private final OrderAllocationService orderAllocationService;
    private final Clock clock;

    public SalesOrderService(
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            DeliveryRepository deliveryRepository,
            PaymentRepository paymentRepository,
            CustomerRepository customerRepository,
            CustomerAddressRepository customerAddressRepository,
            ProductVariantRepository productVariantRepository,
            UserRepository userRepository,
            ApprovalTaskRepository approvalTaskRepository,
            CustomerDebtLedgerRepository customerDebtLedgerRepository,
            PricingService pricingService,
            AuditLogService auditLogService,
            OrderAllocationService orderAllocationService,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.deliveryRepository = deliveryRepository;
        this.paymentRepository = paymentRepository;
        this.customerRepository = customerRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
        this.approvalTaskRepository = approvalTaskRepository;
        this.customerDebtLedgerRepository = customerDebtLedgerRepository;
        this.pricingService = pricingService;
        this.auditLogService = auditLogService;
        this.orderAllocationService = orderAllocationService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderListItemResponse> search(
            String q, String state, String orderType, String paymentStatus, int page, int pageSize) {
        return PageResponse.from(salesOrderRepository
                .findAll(
                        OrderSpecifications.search(
                                q,
                                parseState(state),
                                parseOrderType(orderType),
                                parsePaymentStatus(paymentStatus)),
                        PageRequests.of(page, pageSize, LIST_SORT))
                .map(OrderListItemResponse::from));
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse getDetail(Long id) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        return toDetail(order);
    }

    @Transactional
    public OrderDetailResponse create(OrderInputRequest request, Long actorId) {
        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn khách hàng"));
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể tạo đơn");
        }
        if (request.paymentMethod() == PaymentMethod.CREDIT_NET
                && customer.getCustomerType() != CustomerType.B2B) {
            throw ApiException.badRequest("Chỉ khách B2B được mua công nợ");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Đơn phải có ít nhất một dòng hàng");
        }

        CustomerAddress shipAddress = null;
        if (request.shipAddressId() != null) {
            shipAddress = customerAddressRepository
                    .findByIdAndCustomerId(request.shipAddressId(), customer.getId())
                    .orElseThrow(() -> ApiException.badRequest("Địa chỉ giao hàng không hợp lệ"));
        }

        AppUser placedBy = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        PricingResultResponse priced = pricingService.resolve(new PricingRequest(
                customer.getId(),
                request.paymentMethod().code(),
                moneyOrZero(request.shippingFee()),
                moneyOrZero(request.discountTotal()),
                request.lines()));

        Instant now = Instant.now(clock);
        SalesOrder order = new SalesOrder();
        order.setCode(nextOrderCode());
        order.setCustomer(customer);
        order.setOrderType(request.orderType());
        order.setChannel(request.channel());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentTermDays(
                request.paymentMethod() == PaymentMethod.CREDIT_NET ? customer.getNetDays() : 0);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(request.fulfillmentMethod());
        order.setShipAddress(shipAddress);
        order.setState(OrderState.PENDING);
        order.setSubtotal(priced.subtotal());
        order.setDiscountTotal(moneyOrZero(request.discountTotal()));
        order.setShippingFee(moneyOrZero(request.shippingFee()));
        order.setTotal(priced.total());
        order.setVoucherCode(Strings.trimToNull(request.voucherCode()));
        order.setOverLimit(priced.isOverLimit());
        order.setOverLimitAmount(priced.overLimitAmount());
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

        if (order.isOverLimit()) {
            createOverLimitApproval(order, customer, placedBy);
            addEvent(order, "approval", "Đơn vượt hạn mức, chờ Chủ cửa hàng duyệt", placedBy);
        } else {
            confirmOrder(order, placedBy);
        }

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("total", order.getTotal());
        auditLogService.record(actorId, "create", "sales_order", order.getId(), auditDetail);

        return getDetail(order.getId());
    }

    /**
     * Xác nhận đơn + giữ hàng FEFO. Công nợ ghi trước để không áp hạn 15 phút hold.
     */
    private void confirmOrder(SalesOrder order, AppUser actor) {
        order.setState(OrderState.CONFIRMED);

        if (order.getPaymentMethod() == PaymentMethod.CREDIT_NET && order.getCustomer() != null) {
            chargeDebt(order);
            order.setPaymentStatus(PaymentStatus.DEBT);
        }

        Long actorId = actor == null ? null : actor.getId();
        int held = orderAllocationService.holdForOrder(order, actorId);
        addEvent(
                order,
                "confirmed",
                "Xác nhận đơn — giữ " + held + " khay (FEFO, " + order.getAllocationStatus().code() + ")",
                actor);
    }

    /** Public hook: tạo đơn từ yêu cầu khả thi (SalesRequestService). */
    @Transactional
    public OrderDetailResponse createFromRequest(OrderInputRequest request, Long actorId) {
        return create(request, actorId);
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

    private void createOverLimitApproval(SalesOrder order, Customer customer, AppUser requestedBy) {
        ApprovalTask task = new ApprovalTask();
        task.setApprovalType(ApprovalType.OVER_LIMIT_ORDER);
        task.setRefType("sales_order");
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

    private OrderDetailResponse toDetail(SalesOrder order) {
        List<Delivery> deliveries = deliveryRepository.findByOrder_IdOrderByIdAsc(order.getId());
        Map<Long, Integer> deliveredByLine = new HashMap<>();
        boolean hasDelivered = false;
        for (Delivery delivery : deliveries) {
            if (delivery.getState() == DeliveryState.DELIVERED) {
                hasDelivered = true;
                for (DeliveryItem item : delivery.getItems()) {
                    if (item.orderLineId() == null) {
                        continue;
                    }
                    deliveredByLine.merge(item.orderLineId(), item.qtyKhay(), Integer::sum);
                }
            }
        }

        boolean completedLike =
                order.getState() == OrderState.DELIVERED || order.getState() == OrderState.COMPLETED;
        List<OrderLineViewResponse> lines = order.getLines().stream()
                .map(line -> {
                    int delivered = deliveredByLine.getOrDefault(line.getId(), 0);
                    if (delivered == 0 && deliveries.isEmpty() && completedLike) {
                        delivered = line.getQtyKhay();
                    }
                    return OrderLineViewResponse.from(line, delivered);
                })
                .toList();

        List<Payment> payments = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId()).stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS)
                .toList();
        BigDecimal paidAmount = payments.stream()
                .map(p -> p.getDirection() == PayDirection.IN ? p.getAmount() : p.getAmount().negate())
                .reduce(ZERO, BigDecimal::add);

        List<OrderEventViewResponse> events = orderEventRepository
                .findByOrder_IdOrderByCreatedAtAscIdAsc(order.getId())
                .stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt).reversed()
                        .thenComparing(OrderEvent::getId, Comparator.reverseOrder()))
                .map(OrderEventViewResponse::from)
                .toList();

        boolean blockedByLimit = order.isOverLimit() && order.getApprovedBy() == null;
        List<OrderState> nextStates = blockedByLimit
                ? List.of()
                : MANUAL_NEXT.getOrDefault(order.getState(), List.of());

        boolean canCancel = !hasDelivered
                && order.getState() != OrderState.DELIVERED
                && order.getState() != OrderState.COMPLETED
                && order.getState() != OrderState.CANCELLED
                && order.getState() != OrderState.PARTIAL;

        OrderListItemResponse header = OrderListItemResponse.from(order);
        Customer customer = order.getCustomer();

        return new OrderDetailResponse(
                header.id(),
                header.code(),
                header.customerId(),
                header.orderType(),
                header.channel(),
                header.sourceQuoteId(),
                header.paymentMethod(),
                header.paymentTermDays(),
                header.paymentStatus(),
                header.allocationStatus(),
                header.fulfillmentMethod(),
                header.shipAddressId(),
                header.state(),
                header.subtotal(),
                header.discountTotal(),
                header.shippingFee(),
                header.total(),
                header.voucherCode(),
                header.isOverLimit(),
                header.overLimitAmount(),
                header.approvedBy(),
                header.placedBy(),
                header.placedAt(),
                header.holdExpiresAt(),
                header.note(),
                header.createdAt(),
                header.updatedAt(),
                header.customerName(),
                header.customerType(),
                customer == null ? null : OrderCustomerResponse.from(customer),
                order.getShipAddress() == null ? null : AddressResponse.from(order.getShipAddress()),
                lines,
                deliveries.stream().map(OrderDeliveryViewResponse::from).toList(),
                payments.stream().map(PaymentViewResponse::from).toList(),
                events,
                paidAmount,
                order.getSourceQuote() == null ? null : order.getSourceQuote().getCode(),
                order.getPlacedBy() == null ? null : order.getPlacedBy().getFullName(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getFullName(),
                nextStates,
                canCancel);
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

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
