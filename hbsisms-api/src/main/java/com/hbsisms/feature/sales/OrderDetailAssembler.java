package com.hbsisms.feature.sales;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.sales.dto.OrderAuditTrailItemResponse;
import com.hbsisms.feature.sales.dto.OrderCustomerResponse;
import com.hbsisms.feature.sales.dto.OrderDeliveryViewResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderEventViewResponse;
import com.hbsisms.feature.sales.dto.OrderLineViewResponse;
import com.hbsisms.feature.sales.dto.OrderListItemResponse;
import com.hbsisms.feature.sales.dto.OrderReturnViewResponse;
import com.hbsisms.feature.sales.dto.PaymentViewResponse;
import com.hbsisms.feature.system.AuditLogRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lắp {@link OrderDetailResponse} — Inter 3 dòng 6.
 * Tách khỏi {@link SalesOrderService} để giảm conflict với Duy (create/cancel/transition).
 */
@Component
public class OrderDetailAssembler {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final DeliveryRepository deliveryRepository;
    private final PaymentRepository paymentRepository;
    private final OrderEventRepository orderEventRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final AuditLogRepository auditLogRepository;

    public OrderDetailAssembler(
            DeliveryRepository deliveryRepository,
            PaymentRepository paymentRepository,
            OrderEventRepository orderEventRepository,
            SalesReturnRepository salesReturnRepository,
            AuditLogRepository auditLogRepository) {
        this.deliveryRepository = deliveryRepository;
        this.paymentRepository = paymentRepository;
        this.orderEventRepository = orderEventRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public OrderDetailResponse assemble(SalesOrder order) {
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

        // SUCCESS (thu + hoàn) + PayOS PENDING — Inter 3 dòng 6.
        List<Payment> allPayments = paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(order.getId());
        List<Payment> visiblePayments = allPayments.stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS || p.getStatus() == PayTxnStatus.PENDING)
                .toList();
        BigDecimal paidAmount = allPayments.stream()
                .filter(p -> p.getStatus() == PayTxnStatus.SUCCESS)
                .map(p -> p.getDirection() == PayDirection.IN ? p.getAmount() : p.getAmount().negate())
                .reduce(ZERO, BigDecimal::add);

        List<OrderEventViewResponse> events = orderEventRepository
                .findByOrder_IdOrderByCreatedAtAscIdAsc(order.getId())
                .stream()
                .sorted(Comparator.comparing(OrderEvent::getCreatedAt).reversed()
                        .thenComparing(OrderEvent::getId, Comparator.reverseOrder()))
                .map(OrderEventViewResponse::from)
                .toList();

        List<OrderReturnViewResponse> returns = List.of();
        try {
            returns = salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(order.getId()).stream()
                    .map(OrderReturnViewResponse::from)
                    .toList();
        } catch (Exception ignored) {
            // Khôi chưa ship bảng/repo — detail không 500.
        }

        List<OrderAuditTrailItemResponse> auditTrail = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(SalesOrderService.REF_TYPE, order.getId())
                .stream()
                .map(OrderAuditTrailItemResponse::from)
                .toList();

        List<OrderState> nextStates = SalesOrderService.allowedNextStates(order);

        boolean canCancel = !hasDelivered
                && order.getState() != OrderState.DELIVERED
                && order.getState() != OrderState.COMPLETED
                && order.getState() != OrderState.CANCELLED
                && order.getState() != OrderState.EXPIRED
                && order.getState() != OrderState.PARTIAL
                && order.getState() != OrderState.PREPARING
                && order.getState() != OrderState.TO_SHIP;

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
                header.isPreorder(),
                header.depositAmount(),
                header.depositPercent(),
                header.depositDueAt(),
                header.paidAmount(),
                header.note(),
                header.createdAt(),
                header.updatedAt(),
                header.customerName(),
                header.customerType(),
                customer == null ? null : OrderCustomerResponse.from(customer),
                order.getShipAddress() == null ? null : AddressResponse.from(order.getShipAddress()),
                lines,
                deliveries.stream().map(OrderDeliveryViewResponse::from).toList(),
                visiblePayments.stream().map(PaymentViewResponse::from).toList(),
                events,
                returns,
                auditTrail,
                paidAmount,
                order.getSourceQuote() == null ? null : order.getSourceQuote().getCode(),
                order.getPlacedBy() == null ? null : order.getPlacedBy().getFullName(),
                order.getApprovedBy() == null ? null : order.getApprovedBy().getFullName(),
                nextStates,
                canCancel);
    }
}
