package com.hbsisms.feature.sales;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentReconcileFlag;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.OwnerInboxItemResponse;
import com.hbsisms.feature.sales.dto.OwnerInboxResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gom việc cần Chủ xử lý (Inter 3 dòng 11).
 * Chỉ đọc — không duyệt / hoàn trong API này.
 */
@Service
public class OrderOwnerInboxService {

    public static final String TYPE_DISCOUNT_OR_LIMIT = "discount_or_limit_approval";
    public static final String TYPE_NEEDS_REFUND = "needs_refund";
    public static final String TYPE_PAYMENT_MISMATCH = "payment_mismatch";
    public static final String TYPE_LATE_PAYOS_REFUND = "late_payos_refund";
    public static final String TYPE_DELIVERY_FAILED = "delivery_failed_open";
    public static final String TYPE_RETURN_PENDING = "return_pending_inspect";

    private final ApprovalTaskRepository approvalTaskRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final SalesReturnRepository salesReturnRepository;

    public OrderOwnerInboxService(
            ApprovalTaskRepository approvalTaskRepository,
            SalesOrderRepository salesOrderRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository,
            SalesReturnRepository salesReturnRepository) {
        this.approvalTaskRepository = approvalTaskRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.salesReturnRepository = salesReturnRepository;
    }

    @Transactional(readOnly = true)
    public OwnerInboxResponse list() {
        List<OwnerInboxItemResponse> items = new ArrayList<>();
        Set<Long> latePayosOrderIds = new HashSet<>();

        addApprovalItems(items);
        addLatePayosItems(items, latePayosOrderIds);
        addNeedsRefundItems(items, latePayosOrderIds);
        addPaymentMismatchItems(items);
        addDeliveryFailedItems(items);
        addReturnPendingItems(items);

        items.sort(Comparator.comparing(
                OwnerInboxItemResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new OwnerInboxResponse(List.copyOf(items));
    }

    private void addApprovalItems(List<OwnerInboxItemResponse> items) {
        List<ApprovalTask> tasks = approvalTaskRepository.findByStatusAndRefTypeOrderByCreatedAtDesc(
                ApprovalStatus.PENDING, SalesOrderService.REF_TYPE);
        for (ApprovalTask task : tasks) {
            Long orderId = task.getRefId();
            String code = salesOrderRepository.findById(orderId).map(SalesOrder::getCode).orElse(null);
            String title = task.getTitle() == null || task.getTitle().isBlank()
                    ? "Chờ duyệt đơn #" + orderId
                    : task.getTitle();
            items.add(new OwnerInboxItemResponse(
                    TYPE_DISCOUNT_OR_LIMIT,
                    title,
                    orderId,
                    code,
                    task.getId(),
                    task.getCreatedAt(),
                    "/orders/" + orderId));
        }
    }

    private void addLatePayosItems(List<OwnerInboxItemResponse> items, Set<Long> latePayosOrderIds) {
        List<Long> ids = paymentRepository.findIdsWithNeedsRefundFlag();
        if (ids.isEmpty()) {
            return;
        }
        for (Payment payment : paymentRepository.findAllById(ids)) {
            SalesOrder order = payment.getOrder();
            if (order == null) {
                continue;
            }
            latePayosOrderIds.add(order.getId());
            items.add(new OwnerInboxItemResponse(
                    TYPE_LATE_PAYOS_REFUND,
                    "Tiền về sau khi đơn hết hạn — cần hoàn · " + order.getCode(),
                    order.getId(),
                    order.getCode(),
                    payment.getId(),
                    payment.getPaidAt() != null ? payment.getPaidAt() : order.getUpdatedAt(),
                    "/orders/" + order.getId()));
        }
    }

    private void addNeedsRefundItems(List<OwnerInboxItemResponse> items, Set<Long> latePayosOrderIds) {
        for (SalesOrder order :
                salesOrderRepository.findByPaymentStatusOrderByUpdatedAtDesc(PaymentStatus.REFUND_PENDING)) {
            if (latePayosOrderIds.contains(order.getId())) {
                continue;
            }
            items.add(new OwnerInboxItemResponse(
                    TYPE_NEEDS_REFUND,
                    "Cần hoàn tiền · " + order.getCode(),
                    order.getId(),
                    order.getCode(),
                    order.getId(),
                    order.getUpdatedAt(),
                    "/orders/" + order.getId()));
        }
    }

    private void addPaymentMismatchItems(List<OwnerInboxItemResponse> items) {
        Set<Long> seenOrders = new HashSet<>();

        for (SalesOrder order :
                salesOrderRepository.findByPaymentStatusOrderByUpdatedAtDesc(PaymentStatus.DIFF)) {
            seenOrders.add(order.getId());
            items.add(new OwnerInboxItemResponse(
                    TYPE_PAYMENT_MISMATCH,
                    "Thanh toán lệch tiền · " + order.getCode(),
                    order.getId(),
                    order.getCode(),
                    order.getId(),
                    order.getUpdatedAt(),
                    "/orders/" + order.getId()));
        }

        EnumSet<PaymentReconcileFlag> flags =
                EnumSet.of(PaymentReconcileFlag.SHORT, PaymentReconcileFlag.OVER, PaymentReconcileFlag.DUPLICATE);
        for (Payment payment : paymentRepository.findByReconcileFlagIn(flags)) {
            SalesOrder order = payment.getOrder();
            if (order == null || seenOrders.contains(order.getId())) {
                continue;
            }
            seenOrders.add(order.getId());
            String flagLabel = switch (payment.getReconcileFlag()) {
                case SHORT -> "thiếu";
                case OVER -> "thừa";
                case DUPLICATE -> "trùng";
                default -> "lệch";
            };
            items.add(new OwnerInboxItemResponse(
                    TYPE_PAYMENT_MISMATCH,
                    "Thanh toán lệch tiền (" + flagLabel + ") · " + order.getCode(),
                    order.getId(),
                    order.getCode(),
                    payment.getId(),
                    payment.getPaidAt() != null ? payment.getPaidAt() : order.getUpdatedAt(),
                    "/orders/" + order.getId()));
        }
    }

    private void addDeliveryFailedItems(List<OwnerInboxItemResponse> items) {
        List<SalesOrder> failed = salesOrderRepository.findAll(
                OrderSpecifications.groupSpec(OrderListGroup.DELIVERY_FAILED),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        for (SalesOrder order : failed) {
            Long deliveryId = null;
            Instant when = order.getUpdatedAt();
            for (Delivery d : deliveryRepository.findByOrder_IdOrderByIdAsc(order.getId())) {
                if (d.getState() == DeliveryState.FAILED) {
                    deliveryId = d.getId();
                    if (d.getDeliveredAt() != null) {
                        when = d.getDeliveredAt();
                    }
                }
            }
            items.add(new OwnerInboxItemResponse(
                    TYPE_DELIVERY_FAILED,
                    "Giao thất bại chưa xử lý · " + order.getCode(),
                    order.getId(),
                    order.getCode(),
                    deliveryId != null ? deliveryId : order.getId(),
                    when,
                    "/orders/" + order.getId()));
        }
    }

    private void addReturnPendingItems(List<OwnerInboxItemResponse> items) {
        // Bảng có thể trống nếu Khôi chưa ship — không được 500.
        List<SalesReturn> returns;
        try {
            returns = salesReturnRepository.findByStatusOrderByCreatedAtDesc(ReturnStatus.PENDING);
        } catch (Exception ex) {
            return;
        }
        for (SalesReturn ret : returns) {
            SalesOrder order = ret.getOrder();
            Long orderId = order == null ? null : order.getId();
            String code = order == null ? null : order.getCode();
            items.add(new OwnerInboxItemResponse(
                    TYPE_RETURN_PENDING,
                    "Phiếu trả chờ kiểm · " + (ret.getCode() != null ? ret.getCode() : ("#" + ret.getId())),
                    orderId,
                    code,
                    ret.getId(),
                    ret.getCreatedAt(),
                    orderId == null ? "/returns" : "/orders/" + orderId));
        }
    }
}
