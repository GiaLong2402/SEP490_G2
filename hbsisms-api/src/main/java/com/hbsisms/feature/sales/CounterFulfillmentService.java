package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hoàn tất đơn tại quầy: đã thu đủ → OUT đúng lô HOLD (idempotent) → COMPLETED.
 */
@Service
public class CounterFulfillmentService {

    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;
    private final SalesOrderService salesOrderService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public CounterFulfillmentService(
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService,
            SalesOrderService salesOrderService,
            UserRepository userRepository,
            AuditLogService auditLogService) {
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
        this.salesOrderService = salesOrderService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public OrderDetailResponse completeAtCounter(Long orderId, Long actorId) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));

        if (order.getState() == OrderState.CANCELLED) {
            throw ApiException.conflict("Đơn đã hủy");
        }

        // Idempotent: đã COMPLETED → trả về chi tiết, không OUT thêm.
        if (order.getState() == OrderState.COMPLETED) {
            return salesOrderService.getDetail(orderId);
        }

        if (order.getPaymentStatus() != PaymentStatus.PAID
                && order.getPaymentStatus() != PaymentStatus.DEBT) {
            throw ApiException.conflict("Đơn chưa thu đủ — không thể hoàn tất tại quầy");
        }

        if (order.getState() == OrderState.PENDING) {
            throw ApiException.conflict("Đơn chưa xác nhận");
        }

        AppUser actor = actorId == null
                ? null
                : userRepository.findById(actorId).orElse(null);

        int issued = orderAllocationService.issueOutHeldForOrder(order, actorId);
        order.setState(OrderState.COMPLETED);

        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType("completed_counter");
        event.setDescription(
                issued == 0
                        ? "Hoàn tất quầy (idempotent — không trừ thêm)"
                        : "Hoàn tất quầy — xuất " + issued + " khay");
        event.setCreatedBy(actor);
        orderEventRepository.save(event);

        Map<String, Object> detail = new HashMap<>();
        detail.put("issuedQty", issued);
        auditLogService.record(actorId, "complete_counter", "sales_order", order.getId(), detail);

        return salesOrderService.getDetail(orderId);
    }
}
