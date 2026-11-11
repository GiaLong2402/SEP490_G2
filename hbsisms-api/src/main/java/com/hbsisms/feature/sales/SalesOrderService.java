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
import com.hbsisms.feature.sales.dto.OrderReceiptLineResponse;
import com.hbsisms.feature.sales.dto.OrderReceiptResponse;
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
import java.util.ArrayList;
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
 * Sales order list/detail/create + lifecycle (Inter 2 Phase 2–3).
 * Confirm gọi {@link OrderAllocationService} giữ hàng FEFO.
 */
@Service
public class SalesOrderService {

    public static final String REF_TYPE = OrderAllocationService.REF_TYPE;

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
     * Chủ duyệt/từ chối vượt hạn mức — gọi từ {@link com.hbsisms.feature.approval.ApprovalService}.
     */
    @Transactional
    public void applyOverLimitDecision(Long orderId, boolean approved, AppUser actor, String note) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        if (order.getState() == OrderState.CANCELLED) {
            throw ApiException.conflict("Đơn đã hủy");
        }
        if (order.getApprovedBy() != null || order.getState() != OrderState.PENDING) {
            throw ApiException.conflict("Đơn không còn chờ duyệt vượt hạn mức");
        }

        String suffix = Strings.trimToNull(note) == null ? "" : ": " + note.trim();
        if (approved) {
            order.setApprovedBy(actor);
            addEvent(order, "approved", "Chủ cửa hàng duyệt vượt hạn mức" + suffix, actor);
            confirmOrder(order, actor);
        } else {
            cancelOrderInternal(order, "Từ chối duyệt vượt hạn mức" + suffix, actor, false);
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

        Customer customer = customerRepository
                .findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn khách hàng"));
        if (!customer.isActive()) {
            throw ApiException.conflict("Khách hàng đã bị khóa, không thể sửa đơn");
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
                request.paymentMethod().code(),
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
        order.setOrderType(request.orderType());
        order.setChannel(request.channel());
        order.setPaymentMethod(request.paymentMethod());
        order.setPaymentTermDays(
                request.paymentMethod() == PaymentMethod.CREDIT_NET ? customer.getNetDays() : 0);
        order.setFulfillmentMethod(request.fulfillmentMethod());
        order.setShipAddress(shipAddress);
        order.setSubtotal(priced.subtotal());
        order.setDiscountTotal(moneyOrZero(request.discountTotal()));
        order.setShippingFee(moneyOrZero(request.shippingFee()));
        order.setTotal(priced.total());
        order.setVoucherCode(Strings.trimToNull(request.voucherCode()));
        order.setOverLimit(priced.isOverLimit());
        order.setOverLimitAmount(priced.overLimitAmount());
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
            if (current.getPaymentMethod() == PaymentMethod.CREDIT_NET) {
                chargeDebt(current);
                current.setPaymentStatus(PaymentStatus.DEBT);
            }
            int held = orderAllocationService.holdForOrder(current, actorId);
            addEvent(
                    current,
                    "updated",
                    "Sửa đơn — giữ lại " + held + " khay (" + current.getAllocationStatus().code() + ")",
                    actor);
            salesOrderRepository.save(current);
        } else if (current.isOverLimit() && current.getApprovedBy() == null) {
            approvalTaskRepository
                    .findByRefTypeAndRefIdAndStatus(REF_TYPE, current.getId(), ApprovalStatus.PENDING)
                    .ifPresentOrElse(
                            task -> {
                                task.setTitle(String.format(
                                        Locale.ROOT,
                                        "Đơn %s của %s vượt hạn mức %s ₫",
                                        current.getCode(),
                                        customer.getName(),
                                        current.getOverLimitAmount().toPlainString()));
                                approvalTaskRepository.save(task);
                            },
                            () -> createOverLimitApproval(current, customer, actor));
            addEvent(current, "updated", "Sửa đơn (vẫn chờ duyệt vượt hạn mức)", actor);
            salesOrderRepository.save(current);
        } else if (!current.isOverLimit()) {
            // PENDING không còn vượt hạn → xác nhận như tạo mới.
            approvalTaskRepository
                    .findByRefTypeAndRefIdAndStatus(REF_TYPE, current.getId(), ApprovalStatus.PENDING)
                    .ifPresent(task -> {
                        task.setStatus(ApprovalStatus.APPROVED);
                        task.setNote("Đơn sau khi sửa không còn vượt hạn mức");
                        task.setDecidedAt(Instant.now(clock));
                        task.setDecidedBy(actor);
                        approvalTaskRepository.save(task);
                    });
            addEvent(current, "updated", "Sửa đơn", actor);
            confirmOrder(current, actor);
        } else {
            addEvent(current, "updated", "Sửa đơn", actor);
            salesOrderRepository.save(current);
        }

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("total", current.getTotal());
        auditLogService.record(actorId, "update", REF_TYPE, current.getId(), auditDetail);

        return getDetail(current.getId());
    }

    /**
     * Snapshot biên lai — chỉ khi đã thu đủ hoặc công nợ đã ghi ({@code paid}/{@code debt}).
     * Mỗi lần gọi ghi audit {@code print_receipt}.
     */
    @Transactional
    public OrderReceiptResponse receipt(Long orderId, Long actorId) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        PaymentStatus pay = order.getPaymentStatus();
        if (pay != PaymentStatus.PAID && pay != PaymentStatus.DEBT) {
            throw ApiException.conflict("Chỉ in biên lai khi đã thanh toán đủ hoặc đã ghi công nợ");
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
        String customerName = order.getCustomer() == null ? "Khách lẻ" : order.getCustomer().getName();

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
            confirmOrder(order, actor);
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
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        AppUser actor = requireActor(actorId);
        cancelOrderInternal(order, why, actor, true);
        Map<String, Object> detail = new HashMap<>();
        detail.put("reason", why);
        auditLogService.record(actorId, "cancel", REF_TYPE, order.getId(), detail);
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
        salesOrderRepository.save(order);
    }

    private void cancelOrderInternal(SalesOrder order, String reason, AppUser actor, boolean rejectPendingApproval) {
        OrderState state = order.getState();
        if (state == OrderState.DELIVERED
                || state == OrderState.COMPLETED
                || state == OrderState.CANCELLED
                || state == OrderState.PARTIAL) {
            throw ApiException.conflict("Đơn đã giao hoặc đã hủy, không thể hủy");
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
        if (!hadPaid) {
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
        PaymentStatus pay = order.getPaymentStatus();
        if (pay != PaymentStatus.PAID && pay != PaymentStatus.DEBT) {
            throw ApiException.conflict("Chỉ chuẩn bị khi đã thanh toán đủ (hoặc công nợ đã ghi)");
        }
        if (order.getAllocationStatus() != AllocationStatus.FULL) {
            throw ApiException.conflict("Chưa phân bổ đủ hàng — không thể chuẩn bị");
        }
    }

    private AppUser requireActor(Long actorId) {
        if (actorId == null) {
            throw ApiException.forbidden("Cần đăng nhập");
        }
        return userRepository
                .findById(actorId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
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
