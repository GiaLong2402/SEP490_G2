package com.hbsisms.feature.payment;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.payment.dto.PaymentInputRequest;
import com.hbsisms.feature.payment.dto.PaymentListItemResponse;
import com.hbsisms.feature.payment.dto.PaymentRefundRequest;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.PreorderFulfillmentService;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghi nhận thu (Doanh) + hoàn tiền thủ công (Long Excel #40). */
@Service
public class PaymentService {

    /** Mặc định giao dịch tạo gần nhất lên đầu (theo id) — không phụ thuộc giờ thanh toán (đơn hủy không có). */
    private static final Sort LIST_SORT_NEWEST = Sort.by(Sort.Direction.DESC, "id");
    private static final Sort LIST_SORT_OLDEST = Sort.by(Sort.Direction.ASC, "id");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PaymentRepository paymentRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerDebtLedgerRepository customerDebtLedgerRepository;
    private final OrderEventRepository orderEventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final BusinessCodeService businessCodeService;
    private final PreorderFulfillmentService preorderFulfillmentService;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            SalesOrderRepository salesOrderRepository,
            CustomerRepository customerRepository,
            CustomerDebtLedgerRepository customerDebtLedgerRepository,
            OrderEventRepository orderEventRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            BusinessCodeService businessCodeService,
            PreorderFulfillmentService preorderFulfillmentService,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.customerDebtLedgerRepository = customerDebtLedgerRepository;
        this.orderEventRepository = orderEventRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.businessCodeService = businessCodeService;
        this.preorderFulfillmentService = preorderFulfillmentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentListItemResponse> search(
            String q,
            String method,
            String direction,
            String status,
            String channel,
            Long orderId,
            String reconcileFlag,
            int page,
            int pageSize) {
        return search(q, method, direction, status, channel, orderId, reconcileFlag, null, page, pageSize);
    }

    /** @param sort "asc" = cũ nhất trước; còn lại (mặc định) = mới nhất trước */
    @Transactional(readOnly = true)
    public PageResponse<PaymentListItemResponse> search(
            String q,
            String method,
            String direction,
            String status,
            String channel,
            Long orderId,
            String reconcileFlag,
            String sort,
            int page,
            int pageSize) {
        Sort listSort = "asc".equalsIgnoreCase(sort == null ? "" : sort.trim())
                ? LIST_SORT_OLDEST
                : LIST_SORT_NEWEST;
        return PageResponse.from(paymentRepository
                .findAll(
                        PaymentSpecifications.search(
                                q,
                                parseMethod(method),
                                parseDirection(direction),
                                parseStatus(status),
                                parseChannel(channel),
                                orderId,
                                parseReconcileFlagOptional(reconcileFlag)),
                        PageRequests.of(page, pageSize, listSort))
                .map(PaymentListItemResponse::from));
    }

    @Transactional
    public PaymentListItemResponse markReconcile(Long paymentId, String flagRaw, Long actorId) {
        Payment payment = paymentRepository
                .findWithDetailsById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch"));
        PaymentReconcileFlag flag = parseReconcileFlagRequired(flagRaw);
        payment.setReconcileFlag(flag);
        paymentRepository.save(payment);

        Map<String, Object> audit = new HashMap<>();
        audit.put("reconcileFlag", flag.code());
        auditLogService.record(actorId, "reconcile", "payment", payment.getId(), audit);

        return PaymentListItemResponse.from(
                paymentRepository.findWithDetailsById(payment.getId()).orElse(payment));
    }

    @Transactional
    public PaymentListItemResponse create(PaymentInputRequest request, Long actorId) {
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(ZERO) <= 0) {
            throw ApiException.badRequest("Số tiền phải lớn hơn 0");
        }

        AppUser receiver = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        SalesOrder order = null;
        if (request.orderId() != null) {
            order = salesOrderRepository
                    .findWithDetailsById(request.orderId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
            if (order.getState() == OrderState.CANCELLED) {
                throw ApiException.conflict("Đơn đã hủy");
            }
            if (order.getPaymentMethod() == PaymentMethod.CREDIT_NET
                    && order.getState() == OrderState.PENDING) {
                throw ApiException.conflict("Đơn công nợ chưa được xác nhận, chưa phát sinh nợ");
            }
            // Inter 3 dòng 8: không ghi thu tay khi đang có link PayOS chờ thanh toán.
            assertNoPendingPayosLink(order.getId());
            BigDecimal netPaid = netPaid(order.getId());
            BigDecimal outstanding = order.getTotal().subtract(netPaid);
            if (outstanding.compareTo(ZERO) <= 0) {
                throw ApiException.conflict("Đơn đã thu đủ");
            }
            if (order.isPreorder()
                    && order.getPaymentStatus() == PaymentStatus.UNPAID
                    && order.getDepositAmount() != null
                    && order.getDepositAmount().compareTo(ZERO) > 0
                    && netPaid.compareTo(ZERO) == 0) {
                // Thu đúng số cọc — không thu vượt / thiếu.
                if (amount.compareTo(order.getDepositAmount()) != 0) {
                    throw ApiException.conflict(
                            "Phải thu đúng tiền cọc (" + order.getDepositAmount().toPlainString() + ")");
                }
            } else if (amount.compareTo(outstanding) != 0) {
                // Đơn thường / tất toán: một lần đủ phần còn lại.
                throw ApiException.conflict("Phải thu đủ phần còn lại của đơn (" + outstanding.toPlainString() + ")");
            }
        }

        Customer customer = resolveCustomer(request.customerId(), order);
        if (order == null) {
            if (customer == null) {
                throw ApiException.badRequest("Vui lòng chọn khách hàng");
            }
            if (amount.compareTo(customer.getCurrentDebt()) > 0) {
                throw ApiException.conflict("Số tiền vượt quá công nợ hiện tại");
            }
        }

        Instant now = Instant.now(clock);
        PaymentPurpose purpose = PaymentPurpose.ORDER;
        if (order != null && order.isPreorder()) {
            if (order.getPaymentStatus() == PaymentStatus.UNPAID
                    && order.getDepositAmount() != null
                    && amount.compareTo(order.getDepositAmount()) == 0) {
                purpose = PaymentPurpose.DEPOSIT;
            } else if (order.getPaymentStatus() == PaymentStatus.DEPOSITED
                    || order.getState() == OrderState.AWAITING_SETTLEMENT) {
                purpose = PaymentPurpose.BALANCE;
            }
        }

        Payment payment = new Payment();
        payment.setCode(nextPaymentCode());
        payment.setOrder(order);
        payment.setCustomer(customer);
        payment.setMethod(request.method());
        payment.setDirection(PayDirection.IN);
        payment.setPurpose(purpose);
        payment.setAmount(amount);
        payment.setStatus(PayTxnStatus.SUCCESS);
        payment.setReconcileFlag(PaymentReconcileFlag.NONE);
        payment.setReference(Strings.trimToNull(request.reference()));
        payment.setGateway(gatewayOf(request.method()));
        payment.setPaidAt(now);
        payment.setReceivedBy(receiver);
        payment.setNote(Strings.trimToNull(request.note()));
        paymentRepository.save(payment);

        boolean debtPayment = order == null || order.getPaymentMethod() == PaymentMethod.CREDIT_NET;
        if (debtPayment && customer != null) {
            postDebtPayment(customer, order, payment, amount);
        }

        if (order != null) {
            applyOrderPaymentStatus(order);
            order.setPaidAmount(netPaid(order.getId()));
            salesOrderRepository.save(order);

            OrderEvent event = new OrderEvent();
            event.setOrder(order);
            event.setEventType("payment");
            event.setDescription(
                    (purpose == PaymentPurpose.DEPOSIT ? "Thu cọc " : "Thu ")
                            + amount.toPlainString()
                            + " ₫ ("
                            + payment.getCode()
                            + ")");
            event.setCreatedBy(receiver);
            orderEventRepository.save(event);

            if (purpose == PaymentPurpose.DEPOSIT && order.getPaymentStatus() == PaymentStatus.DEPOSITED) {
                preorderFulfillmentService.onDepositReceived(order.getId(), receiver);
            }
        }

        Map<String, Object> audit = new HashMap<>();
        audit.put("amount", amount);
        auditLogService.record(actorId, "create", "payment", payment.getId(), audit);

        return PaymentListItemResponse.from(
                paymentRepository.findWithDetailsById(payment.getId()).orElse(payment));
    }

    /** Ghi nhận hoàn tiền thủ công (Excel #40) — không gọi PayOS refund API. */
    @Transactional
    public PaymentListItemResponse recordRefund(PaymentRefundRequest request, Long actorId) {
        BigDecimal amount = money(request.amount());
        if (amount.compareTo(ZERO) <= 0) {
            throw ApiException.badRequest("Số tiền phải lớn hơn 0");
        }
        if (request.method() != PaymentMethod.CASH && request.method() != PaymentMethod.BANK_TRANSFER) {
            throw ApiException.badRequest("Phương thức hoàn chỉ hỗ trợ tiền mặt hoặc chuyển khoản");
        }
        // Bằng chứng hoàn tiền (Excel #40): chuyển khoản → mã giao dịch; tiền mặt → ghi rõ đã trả cho ai, ở đâu
        if (request.method() == PaymentMethod.BANK_TRANSFER && Strings.trimToNull(request.reference()) == null) {
            throw ApiException.badRequest("Nhập mã giao dịch chuyển khoản hoàn tiền làm bằng chứng");
        }
        if (request.method() == PaymentMethod.CASH && Strings.trimToNull(request.note()) == null) {
            throw ApiException.badRequest("Hoàn tiền mặt: ghi rõ đã trả cho ai, ở đâu để làm bằng chứng");
        }

        AppUser actor = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        SalesOrder order = salesOrderRepository
                .findWithDetailsById(request.orderId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));

        Payment source = null;
        if (request.sourcePaymentId() != null) {
            source = paymentRepository
                    .findWithDetailsById(request.sourcePaymentId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch nguồn"));
            if (source.getOrder() == null || !request.orderId().equals(source.getOrder().getId())) {
                throw ApiException.badRequest("Giao dịch nguồn không thuộc đơn hàng");
            }
            if (source.getDirection() != PayDirection.IN) {
                throw ApiException.badRequest("Giao dịch nguồn phải là phiếu thu");
            }
        }

        BigDecimal net = netPaid(order.getId());
        if (net.compareTo(ZERO) <= 0) {
            throw ApiException.conflict("Đơn không còn số tiền đã thu để hoàn");
        }
        if (amount.compareTo(net) > 0) {
            throw ApiException.conflict("Số tiền hoàn vượt quá phần đã thu còn lại");
        }

        Instant now = Instant.now(clock);
        Payment refund = new Payment();
        refund.setCode(nextPaymentCode());
        refund.setOrder(order);
        refund.setCustomer(order.getCustomer() != null
                ? order.getCustomer()
                : (source == null ? null : source.getCustomer()));
        refund.setMethod(request.method());
        refund.setDirection(PayDirection.REFUND);
        refund.setAmount(amount);
        refund.setStatus(PayTxnStatus.SUCCESS);
        refund.setReconcileFlag(PaymentReconcileFlag.NONE);
        refund.setReference(Strings.trimToNull(request.reference()));
        refund.setGateway(gatewayOf(request.method()));
        refund.setPaidAt(now);
        refund.setReceivedBy(actor);
        refund.setNote(Strings.trimToNull(request.note()));
        paymentRepository.save(refund);

        clearNeedsRefund(order.getId(), source);

        applyOrderPaymentStatus(order);
        salesOrderRepository.save(order);

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("refund");
        event.setDescription("Hoàn " + amount.toPlainString() + " ₫ (" + refund.getCode() + ")");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        Map<String, Object> audit = new HashMap<>();
        audit.put("amount", amount);
        audit.put("direction", "refund");
        if (source != null) {
            audit.put("sourcePaymentId", source.getId());
        }
        auditLogService.record(actorId, "refund", "payment", refund.getId(), audit);

        return PaymentListItemResponse.from(
                paymentRepository.findWithDetailsById(refund.getId()).orElse(refund));
    }

    /**
     * Task #31 — hoàn theo phiếu trả: công nợ B2B → giảm nợ; còn lại → phiếu {@code REFUND}
     * (TM/CK, khớp #40). Đơn chưa thu / đã hoàn hết → no-op (chỉ đổi status phiếu trả).
     */
    @Transactional
    public void recordRefundForSalesReturn(
            Long orderId, BigDecimal returnAmount, String returnCode, Long actorId) {
        if (orderId == null || returnAmount == null || returnAmount.compareTo(ZERO) <= 0) {
            return;
        }
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        String code = Strings.trimToNull(returnCode) == null ? "trả hàng" : returnCode.trim();

        if (order.getPaymentMethod() == PaymentMethod.CREDIT_NET) {
            if (order.getCustomer() == null) {
                return;
            }
            Customer customer = customerRepository
                    .findById(order.getCustomer().getId())
                    .orElseThrow(() -> ApiException.badRequest("Khách hàng không tồn tại"));
            BigDecimal delta = returnAmount.negate().setScale(2, RoundingMode.HALF_UP);
            customer.setCurrentDebt(customer.getCurrentDebt().add(delta));
            CustomerDebtLedger entry = new CustomerDebtLedger();
            entry.setCustomer(customer);
            entry.setOrder(order);
            entry.setPayment(null);
            entry.setEntryType(DebtEntryType.ADJUST);
            entry.setAmount(delta);
            entry.setBalanceAfter(customer.getCurrentDebt());
            entry.setNote("Giảm nợ do trả hàng " + code);
            customerDebtLedgerRepository.save(entry);
            customerRepository.save(customer);

            AppUser actor = actorId == null
                    ? null
                    : userRepository.findById(actorId).orElse(null);
            OrderEvent event = new OrderEvent();
            event.setOrder(order);
            event.setEventType("refund");
            event.setDescription("Giảm nợ " + returnAmount.toPlainString() + " ₫ — phiếu " + code);
            event.setCreatedBy(actor);
            orderEventRepository.save(event);
            auditLogService.record(
                    actorId, "refund", "sales_return", orderId, Map.of("amount", returnAmount, "creditNet", true));
            return;
        }

        BigDecimal net = netPaid(orderId);
        if (net.compareTo(ZERO) <= 0) {
            return;
        }
        BigDecimal amount = returnAmount.min(net).setScale(2, RoundingMode.HALF_UP);
        PaymentMethod method = order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? PaymentMethod.BANK_TRANSFER
                : PaymentMethod.CASH;
        String reference = method == PaymentMethod.BANK_TRANSFER ? code : null;
        String note = method == PaymentMethod.CASH
                ? "Hoàn tiền mặt phiếu trả " + code + " — đã trả khách"
                : "Hoàn chuyển khoản phiếu trả " + code;
        recordRefund(new PaymentRefundRequest(orderId, null, method, amount, reference, note), actorId);
    }

    /** Inter 3 dòng 8 — đơn đang có PayOS PENDING thì không cho thu CASH/CK tay. */
    private void assertNoPendingPayosLink(Long orderId) {
        boolean pendingPayos = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(orderId).stream()
                .anyMatch(p -> p.getMethod() == PaymentMethod.PAYOS && p.getStatus() == PayTxnStatus.PENDING);
        if (pendingPayos) {
            throw ApiException.conflict(
                    "Đơn đang có link PayOS chờ thanh toán — không ghi thu tay");
        }
    }

    private void clearNeedsRefund(Long orderId, Payment source) {
        if (source != null) {
            clearNeedsRefundOn(source);
            paymentRepository.save(source);
            return;
        }
        List<Payment> payments = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(orderId);
        for (Payment payment : payments) {
            if (payment.getDirection() != PayDirection.IN || payment.getStatus() != PayTxnStatus.SUCCESS) {
                continue;
            }
            if (clearNeedsRefundOn(payment)) {
                paymentRepository.save(payment);
            }
        }
    }

    /** @return true if payload changed */
    private static boolean clearNeedsRefundOn(Payment payment) {
        Map<String, Object> raw = payment.getRawPayload();
        if (raw == null || raw.get("needsRefund") == null) {
            return false;
        }
        Object flag = raw.get("needsRefund");
        boolean needs = flag instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(flag));
        if (!needs) {
            return false;
        }
        Map<String, Object> next = new HashMap<>(raw);
        next.put("needsRefund", false);
        payment.setRawPayload(next);
        return true;
    }

    private void applyOrderPaymentStatus(SalesOrder order) {
        BigDecimal net = netPaid(order.getId());
        if (net.compareTo(ZERO) <= 0) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
            return;
        }
        if (net.compareTo(order.getTotal()) >= 0) {
            order.setPaymentStatus(PaymentStatus.PAID);
            return;
        }
        if (order.isPreorder()
                && order.getDepositAmount() != null
                && order.getDepositAmount().compareTo(ZERO) > 0
                && net.compareTo(order.getDepositAmount()) >= 0) {
            order.setPaymentStatus(PaymentStatus.DEPOSITED);
            return;
        }
        order.setPaymentStatus(PaymentStatus.PARTIAL);
    }

    private Customer resolveCustomer(Long customerId, SalesOrder order) {
        Long id = customerId != null ? customerId : (order == null || order.getCustomer() == null
                ? null
                : order.getCustomer().getId());
        if (id == null) {
            return null;
        }
        return customerRepository
                .findById(id)
                .orElseThrow(() -> ApiException.badRequest("Khách hàng không tồn tại"));
    }

    private void postDebtPayment(Customer customer, SalesOrder order, Payment payment, BigDecimal amount) {
        BigDecimal delta = amount.negate();
        customer.setCurrentDebt(customer.getCurrentDebt().add(delta));
        CustomerDebtLedger entry = new CustomerDebtLedger();
        entry.setCustomer(customer);
        entry.setOrder(order);
        entry.setPayment(payment);
        entry.setEntryType(DebtEntryType.PAYMENT);
        entry.setAmount(delta);
        entry.setBalanceAfter(customer.getCurrentDebt());
        entry.setNote("Thanh toán " + payment.getCode());
        customerDebtLedgerRepository.save(entry);
    }

    private BigDecimal netPaid(Long orderId) {
        BigDecimal in = paymentRepository.sumSuccessfulInByOrderId(orderId);
        BigDecimal refund = paymentRepository.sumSuccessfulRefundByOrderId(orderId);
        BigDecimal inAmt = in == null ? ZERO : in.setScale(2, RoundingMode.HALF_UP);
        BigDecimal refundAmt = refund == null ? ZERO : refund.setScale(2, RoundingMode.HALF_UP);
        return inAmt.subtract(refundAmt);
    }

    private String nextPaymentCode() {
        return businessCodeService.nextPaymentCode();
    }

    private static String gatewayOf(PaymentMethod method) {
        return switch (method) {
            case CASH -> "cash";
            case PAYOS -> "payos";
            default -> "bank";
        };
    }

    private static PaymentMethod parseMethod(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return PaymentMethod.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static PayDirection parseDirection(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "in" -> PayDirection.IN;
            case "refund" -> PayDirection.REFUND;
            default -> throw ApiException.badRequest("Hướng giao dịch không hợp lệ: " + raw);
        };
    }

    private static PayTxnStatus parseStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return PayTxnStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Trạng thái giao dịch không hợp lệ: " + raw);
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

    private static PaymentReconcileFlag parseReconcileFlagOptional(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        return parseReconcileFlagRequired(value);
    }

    private static PaymentReconcileFlag parseReconcileFlagRequired(String raw) {
        try {
            return PaymentReconcileFlag.fromCode(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
