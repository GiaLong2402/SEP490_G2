package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.DeliveryCreateRequest;
import com.hbsisms.feature.sales.dto.DeliveryListItemResponse;
import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tạo giao hàng (mọi đơn có giao hàng, kể cả đơn Sales tạo) — một đơn giao một lần, trọn đơn. Cập nhật đợt giao: bắt đầu giao / đã giao / thất bại (lấy tại quầy: khách đã nhận / không đến).
 * Cách giao chỉ là nhãn: Shop tự giao / Khách đến lấy / Đối tác giao — không gán nhân viên giao.
 * Đồng bộ trạng thái đơn theo tổng khay đã giao ({@code partial} / {@code delivered}).
 */
@Service
public class DeliveryService {

    private static final Map<DeliveryState, Set<DeliveryState>> ALLOWED = Map.of(
            DeliveryState.READY, EnumSet.of(DeliveryState.GOING),
            DeliveryState.GOING, EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED));

    /** Khách lấy tại quầy: không có bước "đang giao" — sẵn sàng → khách đã nhận / khách không đến. */
    private static final Map<DeliveryState, Set<DeliveryState>> ALLOWED_PICKUP = Map.of(
            DeliveryState.READY, EnumSet.of(DeliveryState.DELIVERED, DeliveryState.FAILED));

    /** Mặc định: ngày giao mới nhất trước; cùng ngày thì khung giờ sớm trước (chưa có khung giờ xếp cuối); rồi id mới trước. */
    private static final Sort LIST_SORT_NEWEST = Sort.by(
            Sort.Order.desc("scheduledDate"),
            Sort.Order.asc("timeWindow"),
            Sort.Order.desc("id"));

    /** {@code sort=asc}: ngày giao cũ nhất trước; cùng ngày thì khung giờ sớm trước; rồi id cũ trước. */
    private static final Sort LIST_SORT_OLDEST = Sort.by(
            Sort.Order.asc("scheduledDate"),
            Sort.Order.asc("timeWindow"),
            Sort.Order.asc("id"));

    /** Inter 3 dòng 20: chỉ tạo đợt giao khi đơn Chờ giao / Giao một phần. */
    private static final Set<OrderState> CREATABLE_ORDER_STATES = EnumSet.of(OrderState.TO_SHIP, OrderState.PARTIAL);

    /** Nhãn chuyến giao — mỗi đơn giao một lần, trọn đơn (giống checkout web). */
    static final String WHOLE_ORDER_LABEL = "Trọn đơn";

    private static final DateTimeFormatter EVENT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DeliveryRepository deliveryRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderAllocationService orderAllocationService;
    private final UserRepository userRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final BusinessCodeService businessCodeService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public DeliveryService(
            DeliveryRepository deliveryRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            OrderEventRepository orderEventRepository,
            OrderAllocationService orderAllocationService,
            UserRepository userRepository,
            CustomerAddressRepository customerAddressRepository,
            BusinessCodeService businessCodeService,
            AuditLogService auditLogService,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderAllocationService = orderAllocationService;
        this.userRepository = userRepository;
        this.customerAddressRepository = customerAddressRepository;
        this.businessCodeService = businessCodeService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Danh sách đợt giao cho màn Giao hàng (web nội bộ). */
    @Transactional(readOnly = true)
    public PageResponse<DeliveryListItemResponse> search(
            String q,
            String state,
            String date,
            String timeWindow,
            String fulfillmentMethod,
            String sort,
            int page,
            int pageSize) {
        Sort listSort = "asc".equalsIgnoreCase(sort == null ? "" : sort.trim())
                ? LIST_SORT_OLDEST
                : LIST_SORT_NEWEST;
        Page<Delivery> deliveries = deliveryRepository.findAll(
                DeliverySpecifications.search(
                        q,
                        parseState(state),
                        parseDate(date),
                        timeWindow,
                        parseFulfillmentMethod(fulfillmentMethod)),
                PageRequests.of(page, pageSize, listSort));
        Map<Long, Integer> orderKhay = orderKhayForWholeOrderLegs(deliveries.getContent());
        return PageResponse.from(deliveries.map(
                delivery -> DeliveryListItemResponse.from(delivery, orderKhay.get(delivery.getOrder().getId()))));
    }

    /**
     * Đợt giao "Trọn đơn" (checkout tạo, {@code items} rỗng) giao cả đơn → tổng khay lấy từ các dòng đơn.
     * Gom 1 query cho cả trang.
     */
    private Map<Long, Integer> orderKhayForWholeOrderLegs(List<Delivery> deliveries) {
        Set<Long> orderIds = deliveries.stream()
                .filter(delivery -> delivery.getItems().isEmpty())
                .map(delivery -> delivery.getOrder().getId())
                .collect(Collectors.toSet());
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> totals = new HashMap<>();
        for (Object[] row : deliveryRepository.sumQtyKhayByOrderIds(orderIds)) {
            totals.put((Long) row[0], row[1] == null ? 0 : ((Number) row[1]).intValue());
        }
        return totals;
    }

    /**
     * Tạo giao hàng cho đơn (web nội bộ, nút "Tạo giao hàng" ở chi tiết đơn) — áp dụng cả đơn Sales tạo /
     * từ yêu cầu / từ báo giá (checkout web tự có đợt "Trọn đơn").
     * <ul>
     *   <li>Đơn Chờ giao / Giao một phần; đơn vượt hạn mức phải được Chủ duyệt.</li>
     *   <li>Ngày giao bắt buộc, không ở quá khứ; khung giờ (nếu có) thuộc {@link DeliveryTimeWindows}.</li>
     *   <li>Giao tận nơi / đối tác: cần địa chỉ (mặc định địa chỉ trên đơn) thuộc đúng khách của đơn;
     *       khoảng cách / trong bán kính chép từ địa chỉ (đã tính bằng Goong lúc lưu địa chỉ).</li>
     *   <li>Cách giao chỉ là nhãn (Shop tự giao / Khách đến lấy / Đối tác giao), không gán nhân viên giao.</li>
     *   <li>Một đơn giao một lần, trọn đơn: đơn đã có chuyến chưa thất bại → 409; chuyến trước thất bại
     *       thì được tạo chuyến mới (giao lại). Mỗi dòng phải giao đủ số khay đặt.</li>
     *   <li>Mã chuyến giao theo dãy chung {@code DL-<năm>-<số>} ({@link BusinessCodeService}).</li>
     * </ul>
     * Khóa dòng đơn trong lúc kiểm tra để 2 người tạo cùng lúc không ra 2 chuyến.
     */
    @Transactional
    public DeliveryListItemResponse create(DeliveryCreateRequest request, Long actorId) {
        if (request == null || request.orderId() == null) {
            throw ApiException.badRequest("Thiếu đơn hàng");
        }
        SalesOrder order = salesOrderRepository
                .findByIdForUpdate(request.orderId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        if (order.isOverLimit() && order.getApprovedBy() == null) {
            throw ApiException.conflict("Đơn vượt hạn mức, cần Chủ cửa hàng duyệt trước");
        }
        if (!CREATABLE_ORDER_STATES.contains(order.getState())) {
            throw ApiException.conflict("Chỉ tạo đợt giao khi đơn ở trạng thái Chờ giao hoặc Giao một phần");
        }

        FulfillmentMethod method = request.fulfillmentMethod() != null
                ? request.fulfillmentMethod()
                : order.getFulfillmentMethod();

        LocalDate scheduledDate = parseDate(request.scheduledDate());
        if (scheduledDate == null) {
            throw ApiException.badRequest("Vui lòng chọn ngày giao");
        }
        if (scheduledDate.isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("Ngày giao không được ở quá khứ");
        }
        String timeWindow = Strings.trimToNull(request.timeWindow());
        if (timeWindow != null && !DeliveryTimeWindows.isValid(timeWindow)) {
            throw ApiException.badRequest(
                    "Khung giờ không hợp lệ (chọn " + String.join(", ", DeliveryTimeWindows.ALL) + ")");
        }

        CustomerAddress address = method == FulfillmentMethod.PICKUP ? null : resolveAddress(order, request.addressId());

        List<Delivery> existing = deliveryRepository.findByOrder_IdOrderByIdAsc(order.getId());
        if (existing.stream().anyMatch(d -> d.getState() != DeliveryState.FAILED)) {
            throw ApiException.conflict(
                    "Đơn đã có chuyến giao — mỗi đơn giao một lần (trọn đơn), chỉ tạo lại khi chuyến trước thất bại");
        }
        int attempt = existing.size() + 1;
        List<SalesOrderLine> lines = salesOrderLineRepository.findByOrder_IdOrderByIdAsc(order.getId());
        List<DeliveryItem> items = wholeOrderItems(lines, request.items());
        String legLabel = WHOLE_ORDER_LABEL;

        Delivery delivery = new Delivery();
        delivery.setOrder(order);
        delivery.setCode(businessCodeService.nextDeliveryCode());
        delivery.setLegLabel(legLabel);
        delivery.setScheduledDate(scheduledDate);
        delivery.setTimeWindow(timeWindow);
        delivery.setFulfillmentMethod(method);
        delivery.setAddress(address);
        delivery.setInRadius(address == null || address.isInDeliveryRadius());
        delivery.setDistanceKm(address == null ? null : address.getDistanceKm());
        delivery.setItems(items);
        delivery.setState(DeliveryState.READY);
        delivery.setNote(Strings.trimToNull(request.note()));
        Delivery saved = deliveryRepository.save(delivery);

        int qtyKhay = items.stream().mapToInt(DeliveryItem::qtyKhay).sum();
        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        addEvent(order, "delivery", "Tạo giao hàng " + legLabel
                + (attempt > 1 ? " (giao lại lần " + attempt + ")" : "")
                + " · " + scheduledDate.format(EVENT_DATE)
                + (timeWindow == null ? "" : " " + timeWindow) + " · " + qtyKhay + " khay", actor);

        Map<String, Object> detail = new HashMap<>();
        detail.put("orderId", order.getId());
        detail.put("legLabel", legLabel);
        detail.put("attempt", attempt);
        detail.put("scheduledDate", scheduledDate.toString());
        detail.put("fulfillmentMethod", method.code());
        detail.put("qtyKhay", qtyKhay);
        auditLogService.record(actorId, "create", "delivery", saved.getId(), detail);

        return DeliveryListItemResponse.from(saved, null);
    }

    /** Địa chỉ giao: gửi lên hoặc địa chỉ trên đơn; phải thuộc đúng khách của đơn. */
    private CustomerAddress resolveAddress(SalesOrder order, Long requestedAddressId) {
        Long addressId = requestedAddressId != null
                ? requestedAddressId
                : (order.getShipAddress() == null ? null : order.getShipAddress().getId());
        if (addressId == null) {
            throw ApiException.badRequest("Đợt giao tận nơi cần địa chỉ giao hàng");
        }
        CustomerAddress address = customerAddressRepository
                .findById(addressId)
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy địa chỉ giao hàng"));
        Long orderCustomerId = order.getCustomer() == null ? null : order.getCustomer().getId();
        Long addressCustomerId = address.getCustomer() == null ? null : address.getCustomer().getId();
        if (orderCustomerId == null || !orderCustomerId.equals(addressCustomerId)) {
            throw ApiException.badRequest("Địa chỉ không thuộc khách của đơn");
        }
        return address;
    }

    /**
     * Một đơn giao một lần, trọn đơn: mỗi dòng phải giao đủ số khay đã đặt.
     * {@code requested} rỗng → hệ thống tự lấy đủ các dòng của đơn.
     */
    private static List<DeliveryItem> wholeOrderItems(List<SalesOrderLine> orderLines, List<DeliveryItem> requested) {
        List<DeliveryItem> full = orderLines.stream()
                .filter(line -> line.getQtyKhay() > 0)
                .map(line -> new DeliveryItem(line.getId(), line.getQtyKhay()))
                .toList();
        if (full.isEmpty()) {
            throw ApiException.conflict("Đơn không có khay nào để giao");
        }
        if (requested == null || requested.isEmpty()) {
            return full;
        }
        Set<Long> orderLineIds = orderLines.stream().map(SalesOrderLine::getId).collect(Collectors.toSet());
        Map<Long, Integer> got = new HashMap<>();
        for (DeliveryItem item : requested) {
            if (item == null || item.orderLineId() == null) {
                throw ApiException.badRequest("Thiếu dòng hàng trong chuyến giao");
            }
            if (!orderLineIds.contains(item.orderLineId())) {
                throw ApiException.badRequest("Dòng hàng không thuộc đơn này");
            }
            if (got.put(item.orderLineId(), item.qtyKhay()) != null) {
                throw ApiException.badRequest("Dòng hàng bị lặp trong chuyến giao");
            }
        }
        for (SalesOrderLine line : orderLines) {
            if (got.getOrDefault(line.getId(), 0) != line.getQtyKhay()) {
                throw ApiException.conflict("Mỗi đơn giao một lần, trọn đơn — dòng #" + line.getId()
                        + " phải giao đủ " + line.getQtyKhay() + " khay");
            }
        }
        return full;
    }

    /**
     * Cập nhật đợt giao: đổi trạng thái (bắt đầu giao / đã giao / thất bại) và/hoặc ghi chú.
     * Bắt đầu giao không cần gán người giao — cách giao chỉ là nhãn; lưu giờ bắt đầu để khách theo dõi.
     * Khóa dòng đợt giao để 2 người bấm cùng lúc không chuyển trạng thái 2 lần.
     */
    @Transactional
    public void update(Long id, DeliveryUpdateRequest request, Long actorId) {
        String note = request == null ? null : Strings.trimToNull(request.note());
        if (request == null || (request.state() == null && note == null)) {
            throw ApiException.badRequest("Không có trường nào để cập nhật");
        }
        Delivery delivery = deliveryRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt giao"));

        if (delivery.getState() == DeliveryState.DELIVERED) {
            throw ApiException.conflict("Đợt giao đã hoàn tất");
        }
        if (delivery.getState() == DeliveryState.FAILED && request.state() != null) {
            throw ApiException.conflict("Đợt giao đã thất bại, không thể đổi trạng thái");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        if (note != null) {
            delivery.setNote(note);
        }
        if (request.state() != null) {
            applyState(delivery, request.state(), note, actor, actorId);
        }
        deliveryRepository.save(delivery);
    }

    private void applyState(
            Delivery delivery, DeliveryState target, String note, AppUser actor, Long actorId) {
        DeliveryState current = delivery.getState();
        boolean pickup = delivery.getFulfillmentMethod() == FulfillmentMethod.PICKUP;
        Set<DeliveryState> allowed = (pickup ? ALLOWED_PICKUP : ALLOWED).getOrDefault(current, Set.of());
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

        if (target == DeliveryState.DELIVERED
                && CollectOnDelivery.matches(order)
                && order.getPaymentStatus() != PaymentStatus.PAID) {
            throw ApiException.conflict(
                    "Đơn COD — cần ghi nhận thanh toán đủ trước khi xác nhận đã giao");
        }

        delivery.setState(target);
        String leg = delivery.getLegLabel() == null ? delivery.getCode() : delivery.getLegLabel();

        if (target == DeliveryState.GOING) {
            delivery.setStartedAt(Instant.now(clock));
            addEvent(order, "delivery", "Bắt đầu giao " + leg + " · " + methodLabel(delivery.getFulfillmentMethod()), actor);
        } else if (target == DeliveryState.FAILED) {
            String reason = note == null ? "" : ": " + note;
            addEvent(order, "delivery", (pickup ? "Khách không đến lấy " : "Giao thất bại ") + leg + reason, actor);
        } else if (target == DeliveryState.DELIVERED) {
            delivery.setDeliveredAt(Instant.now(clock));
            addEvent(order, "delivery", (pickup ? "Khách đã nhận " : "Đã giao ") + leg, actor);
            syncOrderAfterDelivery(order, actor, actorId);
        }

        Map<String, Object> detail = new HashMap<>();
        detail.put("state", target.code());
        if (note != null) {
            detail.put("note", note);
        }
        auditLogService.record(actorId, "update", "delivery", delivery.getId(), detail);
    }

    /** Nhãn cách giao (khớp {@code fulfillmentLabels} của web nội bộ). */
    static String methodLabel(FulfillmentMethod method) {
        if (method == null) {
            return "Shop tự giao";
        }
        return switch (method) {
            case SHOP_DELIVERY -> "Shop tự giao";
            case PICKUP -> "Khách đến lấy";
            case PARTNER_DELIVERY -> "Đối tác giao";
        };
    }

    /**
     * Đồng bộ trạng thái đơn theo tổng khay đã giao. Đợt giao không có danh sách hàng ({@code items} rỗng —
     * đợt "Trọn đơn" checkout web tự tạo) được tính là giao đủ cả đơn, giống cách màn Giao hàng hiển thị.
     */
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
            List<DeliveryItem> items = d.getItems();
            if (items == null || items.isEmpty()) {
                deliveredKhay = Math.max(deliveredKhay, ordered);
                continue;
            }
            for (DeliveryItem item : items) {
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

    private static DeliveryState parseState(String raw) {
        if (Strings.trimToNull(raw) == null) {
            return null;
        }
        try {
            return DeliveryState.fromCode(raw);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }

    private static FulfillmentMethod parseFulfillmentMethod(String raw) {
        if (Strings.trimToNull(raw) == null) {
            return null;
        }
        try {
            return FulfillmentMethod.fromCode(raw);
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
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("Ngày giao không hợp lệ (định dạng yyyy-MM-dd): " + raw);
        }
    }
}
