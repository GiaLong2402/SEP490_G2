package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cập nhật đợt giao: bắt đầu giao / đã giao / thất bại / gán tài xế.
 * Đồng bộ trạng thái đơn theo tổng khay đã giao ({@code partial} / {@code delivered}).
 */
@Service
public class DeliveryService {

    private static final Map<DeliveryState, Set<DeliveryState>> ALLOWED = Map.of(
            DeliveryState.READY, EnumSet.of(DeliveryState.GOING),
            DeliveryState.GOING, EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED));

    private final DeliveryRepository deliveryRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService,
            UserRepository userRepository,
            AuditLogService auditLogService,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public void update(Long id, DeliveryUpdateRequest request, Long actorId) {
        Delivery delivery = deliveryRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt giao"));

        if (delivery.getState() == DeliveryState.DELIVERED) {
            throw ApiException.conflict("Đợt giao đã hoàn tất");
        }
        if (delivery.getState() == DeliveryState.FAILED && request.state() != null) {
            throw ApiException.conflict("Đợt giao đã thất bại, không thể đổi trạng thái");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        boolean changed = false;

        // FE: { assignedTo } riêng khi phân công; { state } khi đổi trạng thái (không đụng assignee).
        boolean assignOnly = request.state() == null;
        if (assignOnly || request.assignedTo() != null) {
            if (request.assignedTo() == null) {
                delivery.setAssignedTo(null);
            } else {
                AppUser courier = userRepository
                        .findById(request.assignedTo())
                        .orElseThrow(() -> ApiException.badRequest("Không tìm thấy nhân viên giao hàng"));
                delivery.setAssignedTo(courier);
            }
            changed = true;
        }

        String note = Strings.trimToNull(request.note());
        if (note != null) {
            delivery.setNote(note);
            changed = true;
        }

        if (request.state() != null) {
            applyState(delivery, request.state(), note, actor, actorId);
            changed = true;
        }

        if (!changed) {
            throw ApiException.badRequest("Không có trường nào để cập nhật");
        }

        deliveryRepository.save(delivery);
    }

    private void applyState(
            Delivery delivery, DeliveryState target, String note, AppUser actor, Long actorId) {
        DeliveryState current = delivery.getState();
        Set<DeliveryState> allowed = ALLOWED.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw ApiException.conflict("Không thể chuyển đợt giao sang trạng thái này");
        }

        SalesOrder order = delivery.getOrder();
        // Chỉ giao khi đơn đã qua xác nhận → chuẩn bị → sẵn sàng giao (hoặc đang giao dở).
        OrderState orderState = order.getState();
        if (orderState != OrderState.TO_SHIP && orderState != OrderState.PARTIAL) {
            throw ApiException.conflict(
                    "Chỉ được bắt đầu/cập nhật giao khi đơn ở trạng thái Chờ giao hoặc Giao một phần");
        }

        delivery.setState(target);
        String leg = delivery.getLegLabel() == null ? delivery.getCode() : delivery.getLegLabel();

        if (target == DeliveryState.GOING) {
            addEvent(order, "delivery", "Bắt đầu giao " + leg, actor);
        } else if (target == DeliveryState.FAILED) {
            String reason = note == null ? "" : ": " + note;
            addEvent(order, "delivery", "Giao thất bại " + leg + reason, actor);
        } else if (target == DeliveryState.DELIVERED) {
            delivery.setDeliveredAt(Instant.now(clock));
            addEvent(order, "delivery", "Đã giao " + leg, actor);
            syncOrderAfterDelivery(order, actor, actorId);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("state", target.code());
        if (note != null) {
            detail.put("note", note);
        }
        auditLogService.record(actorId, "update", "delivery", delivery.getId(), detail);
    }

    private void syncOrderAfterDelivery(SalesOrder order, AppUser actor, Long actorId) {
        Long orderId = order.getId();
        int ordered = salesOrderLineRepository.findByOrder_IdOrderByIdAsc(orderId).stream()
                .mapToInt(SalesOrderLine::getQtyKhay)
                .sum();
        int deliveredKhay = 0;
        for (Delivery d : deliveryRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            if (d.getState() != DeliveryState.DELIVERED) {
                continue;
            }
            for (DeliveryItem item : d.getItems()) {
                deliveredKhay += item.qtyKhay();
            }
        }

        OrderState next;
        if (ordered > 0 && deliveredKhay >= ordered) {
            next = OrderState.DELIVERED;
        } else if (deliveredKhay > 0) {
            next = OrderState.PARTIAL;
        } else {
            return;
        }

        if (order.getState() == next
                || order.getState() == OrderState.COMPLETED
                || order.getState() == OrderState.CANCELLED) {
            return;
        }

        if (next == OrderState.DELIVERED) {
            orderAllocationService.issueOutHeldForOrder(order, actorId);
            order.setState(OrderState.DELIVERED);
            salesOrderRepository.save(order);
            addEvent(order, "delivered", "Đã giao đủ hàng", actor);
        } else {
            order.setState(OrderState.PARTIAL);
            salesOrderRepository.save(order);
        }
    }

    private void addEvent(SalesOrder order, String type, String description, AppUser actor) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(type);
        event.setDescription(description);
        event.setCreatedBy(actor);
        orderEventRepository.save(event);
    }
}
