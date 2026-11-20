package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.LedgerEntryType;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.LocationRepository;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockLedger;
import com.hbsisms.feature.inventory.StockLedgerRepository;
import com.hbsisms.feature.inventory.StockMutation;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.StockService;
import com.hbsisms.feature.payment.PaymentService;
import com.hbsisms.feature.sales.dto.ReturnInputRequest;
import com.hbsisms.feature.sales.dto.ReturnInspectDefaultsResponse;
import com.hbsisms.feature.sales.dto.ReturnInspectLine;
import com.hbsisms.feature.sales.dto.ReturnItemViewResponse;
import com.hbsisms.feature.sales.dto.ReturnLineInput;
import com.hbsisms.feature.sales.dto.ReturnListItemResponse;
import com.hbsisms.feature.sales.dto.ReturnableLineResponse;
import com.hbsisms.feature.sales.dto.ReturnableOrderResponse;
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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inter 3 task #29–#31 — phiếu trả, nhận hàng (cộng tồn), hoàn tiền (ghi payment / giảm nợ).
 * Scope basic: không route khu hàng trả / QC đạt-không đạt.
 */
@Service
public class SalesReturnService {

    private static final String REF_TYPE = "sales_return";
    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "createdAt");
    private static final Set<OrderState> RETURNABLE_STATES =
            EnumSet.of(OrderState.PARTIAL, OrderState.DELIVERED, OrderState.COMPLETED);
    private static final Map<ReturnStatus, Set<ReturnStatus>> STATUS_FLOW = new EnumMap<>(ReturnStatus.class);

    static {
        STATUS_FLOW.put(ReturnStatus.PENDING, EnumSet.of(ReturnStatus.RECEIVED, ReturnStatus.REJECTED));
        STATUS_FLOW.put(ReturnStatus.RECEIVED, EnumSet.of(ReturnStatus.REFUNDED));
    }

    private final SalesReturnRepository salesReturnRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final DeliveryRepository deliveryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OrderEventRepository orderEventRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final StockService stockService;
    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final LocationRepository locationRepository;
    private final OrderAllocationService orderAllocationService;
    private final PaymentService paymentService;
    private final Clock clock;

    public SalesReturnService(
            SalesReturnRepository salesReturnRepository,
            SalesOrderRepository salesOrderRepository,
            SalesOrderLineRepository salesOrderLineRepository,
            DeliveryRepository deliveryRepository,
            ProductVariantRepository productVariantRepository,
            OrderEventRepository orderEventRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            StockService stockService,
            StockRepository stockRepository,
            StockLedgerRepository stockLedgerRepository,
            LocationRepository locationRepository,
            OrderAllocationService orderAllocationService,
            PaymentService paymentService,
            Clock clock) {
        this.salesReturnRepository = salesReturnRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.salesOrderLineRepository = salesOrderLineRepository;
        this.deliveryRepository = deliveryRepository;
        this.productVariantRepository = productVariantRepository;
        this.orderEventRepository = orderEventRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.stockService = stockService;
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.locationRepository = locationRepository;
        this.orderAllocationService = orderAllocationService;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReturnListItemResponse> search(String q, String status, int page, int pageSize) {
        ReturnStatus parsed = parseStatus(status);
        return PageResponse.from(salesReturnRepository
                .findAll(ReturnSpecifications.search(q, parsed), PageRequests.of(page, pageSize, LIST_SORT))
                .map(this::toListItem));
    }

    /**
     * Gợi ý khi nhận hàng: lô/vị trí đã xuất (cộng tồn khả dụng). Không dùng khu hàng trả.
     */
    @Transactional(readOnly = true)
    public ReturnInspectDefaultsResponse inspectDefaults(Long id) {
        SalesReturn row = salesReturnRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu trả"));
        Long orderId = row.getOrder() == null ? null : row.getOrder().getId();

        List<ReturnInspectDefaultsResponse.Line> lines = new ArrayList<>();
        for (ReturnItem item : row.getItems()) {
            StockTarget shipped = null;
            try {
                shipped = resolveStockTarget(orderId, item.variantId(), item.lotId(), null);
            } catch (ApiException ignored) {
                // Không suy được vị trí xuất → FE fallback danh sách vị trí.
            }
            Long shippedLocId = shipped == null ? null : shipped.locationId();
            String shippedLabel = shippedLocId == null ? null : locationLabel(requireLocation(shippedLocId));
            lines.add(new ReturnInspectDefaultsResponse.Line(
                    item.variantId(),
                    shipped == null ? item.lotId() : shipped.lotId(),
                    shippedLocId,
                    shippedLabel,
                    null,
                    null));
        }
        return new ReturnInspectDefaultsResponse(null, null, lines);
    }

    @Transactional(readOnly = true)
    public List<ReturnableOrderResponse> returnableOrders(String q, Long deliveryId) {
        if (deliveryId != null) {
            ReturnableOrderResponse one = returnableForFailedDelivery(deliveryId);
            return one == null ? List.of() : List.of(one);
        }
        String term = Strings.trimToNull(q);
        return salesOrderRepository
                .findAll((root, query, cb) -> {
                    if (query != null && SalesOrder.class.equals(query.getResultType())) {
                        root.fetch("customer", jakarta.persistence.criteria.JoinType.LEFT);
                        query.distinct(true);
                    }
                    var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
                    predicates.add(root.get("state").in(RETURNABLE_STATES));
                    if (term != null) {
                        String pattern = "%" + term.toLowerCase(Locale.ROOT) + "%";
                        var customer = root.join("customer", jakarta.persistence.criteria.JoinType.LEFT);
                        predicates.add(cb.or(
                                cb.like(cb.lower(root.get("code")), pattern),
                                cb.like(cb.lower(customer.get("name")), pattern)));
                    }
                    return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
                }, Sort.by(Sort.Direction.DESC, "placedAt"))
                .stream()
                .limit(100)
                .map(order -> {
                    List<ReturnableLineResponse> lines = returnableLinesForOrder(order.getId());
                    if (lines.isEmpty()) {
                        return null;
                    }
                    String customerName =
                            order.getCustomer() == null ? "Khách lẻ" : order.getCustomer().getName();
                    return new ReturnableOrderResponse(order.getId(), order.getCode(), customerName, lines);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Đợt giao thất bại còn SL nhận về → một “đơn” để FE mở phiếu nhận hàng về. */
    private ReturnableOrderResponse returnableForFailedDelivery(Long deliveryId) {
        Delivery delivery = deliveryRepository
                .findById(deliveryId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đợt giao"));
        if (delivery.getState() != DeliveryState.FAILED) {
            throw ApiException.conflict("Chỉ nhận hàng về từ đợt giao thất bại");
        }
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(delivery.getOrder().getId())
                .orElse(delivery.getOrder());
        Map<Long, Integer> maxByVariant = maxQtyForFailedDelivery(delivery);
        if (maxByVariant.isEmpty()) {
            return null;
        }
        List<ReturnableLineResponse> lines = new ArrayList<>();
        for (SalesOrderLine line : salesOrderLineRepository.findByOrder_IdOrderByIdAsc(order.getId())) {
            Long variantId = line.getVariant().getId();
            Integer maxQty = maxByVariant.get(variantId);
            if (maxQty == null || maxQty <= 0) {
                continue;
            }
            ProductVariant variant = line.getVariant();
            String productName = variant.getProduct() == null ? variant.getSku() : variant.getProduct().getName();
            lines.add(new ReturnableLineResponse(
                    variantId,
                    variant.getSku(),
                    productName,
                    variant.getPackLabel(),
                    line.getUnitPrice(),
                    maxQty));
        }
        if (lines.isEmpty()) {
            return null;
        }
        String customerName = order.getCustomer() == null ? "Khách lẻ" : order.getCustomer().getName();
        return new ReturnableOrderResponse(order.getId(), order.getCode(), customerName, lines);
    }

    @Transactional
    public ReturnListItemResponse create(ReturnInputRequest request, Long actorId) {
        SalesOrder order = salesOrderRepository
                .findWithDetailsById(request.orderId())
                .orElseThrow(() -> ApiException.badRequest("Vui lòng chọn đơn hàng"));

        Delivery delivery = null;
        Map<Long, Integer> maxByVariant;
        if (request.deliveryId() != null) {
            delivery = deliveryRepository
                    .findById(request.deliveryId())
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy đợt giao"));
            if (!Objects.equals(delivery.getOrder().getId(), order.getId())) {
                throw ApiException.badRequest("Đợt giao không thuộc đơn đã chọn");
            }
            if (delivery.getState() != DeliveryState.FAILED) {
                throw ApiException.conflict("Chỉ nhận hàng về từ đợt giao thất bại");
            }
            maxByVariant = maxQtyForFailedDelivery(delivery);
        } else {
            if (!RETURNABLE_STATES.contains(order.getState())) {
                throw ApiException.conflict("Chỉ trả hàng khi đơn đã giao / giao một phần / hoàn tất");
            }
            maxByVariant = maxQtyForCustomerReturn(order.getId());
        }

        List<ReturnItem> items = buildItems(request.items(), maxByVariant, order);
        if (items.isEmpty()) {
            throw ApiException.badRequest("Phiếu trả phải có ít nhất một khay");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);

        SalesReturn row = new SalesReturn();
        row.setCode(nextCode());
        row.setOrder(order);
        row.setDelivery(delivery);
        row.setCustomer(order.getCustomer());
        row.setReason(request.reason().trim());
        row.setItems(items);
        row.setAmount(items.stream().map(ReturnItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add));
        row.setStatus(ReturnStatus.PENDING);
        row.setCreatedBy(actor);
        row = salesReturnRepository.save(row);

        String eventDesc = delivery == null
                ? "Tạo phiếu trả " + row.getCode()
                : "Tạo phiếu nhận hàng về " + row.getCode() + " (đợt giao #" + delivery.getId() + ")";
        addEvent(order, "return", eventDesc, actor);
        auditLogService.record(
                actorId,
                "create",
                "sales_return",
                row.getId(),
                Map.of(
                        "code", row.getCode(),
                        "orderId", order.getId(),
                        "deliveryId", delivery == null ? "" : delivery.getId(),
                        "amount", row.getAmount()));

        return toListItem(salesReturnRepository.findWithDetailsById(row.getId()).orElse(row));
    }

    /**
     * Chuyển trạng thái phiếu. {@code received} = task #30; {@code refunded} = task #31 (ghi hoàn /
     * giảm nợ).
     */
    @Transactional
    public void setStatus(Long id, ReturnStatus target, List<ReturnInspectLine> inspections, Long actorId) {
        SalesReturn row = salesReturnRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu trả"));
        Set<ReturnStatus> allowed = STATUS_FLOW.getOrDefault(row.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw ApiException.conflict("Không thể chuyển sang trạng thái này");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);

        if (target == ReturnStatus.RECEIVED) {
            applyReceived(row, inspections, actorId);
            row.setInspectedBy(actor);
            row.setInspectedAt(Instant.now(clock));
        }
        if (target == ReturnStatus.REFUNDED) {
            applyRefund(row, actorId);
        }

        row.setStatus(target);
        salesReturnRepository.save(row);

        if (row.getOrder() != null) {
            String label = switch (target) {
                case RECEIVED -> "đã nhận hàng";
                case REFUNDED -> "đã hoàn tiền";
                case REJECTED -> "đã hủy";
                default -> target.code();
            };
            addEvent(row.getOrder(), "return", "Phiếu trả " + row.getCode() + ": " + label, actor);
        }
        auditLogService.record(actorId, "update", "sales_return", row.getId(), Map.of("status", target.code()));
    }

    /** Task #31 — ghi phiếu hoàn / giảm nợ theo {@code amount} phiếu trả. */
    private void applyRefund(SalesReturn row, Long actorId) {
        SalesOrder order = row.getOrder();
        if (order == null) {
            throw ApiException.badRequest("Phiếu trả thiếu đơn hàng");
        }
        paymentService.recordRefundForSalesReturn(
                order.getId(), row.getAmount(), row.getCode(), actorId);
    }

    /**
     * - Giao thất bại + còn HOLD: nhả hold, không nhập kho.
     * - Khách trả (đã OUT): cộng tồn khả dụng về vị trí đã xuất (hoặc vị trí FE gửi).
     */
    private void applyReceived(SalesReturn row, List<ReturnInspectLine> inspections, Long actorId) {
        SalesOrder order = row.getOrder();
        if (order == null) {
            throw ApiException.badRequest("Phiếu trả thiếu đơn hàng");
        }

        Map<Long, Integer> qtyByVariant = new LinkedHashMap<>();
        for (ReturnItem item : row.getItems()) {
            qtyByVariant.merge(item.variantId(), item.qty(), Integer::sum);
        }

        boolean failedDelivery = row.getDelivery() != null;
        boolean stillHeld = failedDelivery
                && orderAllocationService.netHeldByLot(order.getId()).values().stream().anyMatch(q -> q > 0);

        if (stillHeld) {
            // Chưa OUT: chỉ nhả HOLD về khả dụng — không nhập thêm (tránh nhân đôi tồn).
            orderAllocationService.releaseHeldForVariants(
                    order,
                    qtyByVariant,
                    actorId,
                    "Nhả hold — nhận hàng về phiếu " + row.getCode());
            return;
        }

        if (inspections != null && !inspections.isEmpty()) {
            applyReceiveLines(row, inspections, actorId);
            return;
        }

        // Không gửi chi tiết → cộng tồn về lô/vị trí đã xuất.
        for (ReturnItem item : row.getItems()) {
            StockTarget shipped = resolveStockTarget(order.getId(), item.variantId(), item.lotId(), null);
            stockService.receiveReturn(StockMutation.of(
                    shipped.variantId(),
                    shipped.lotId(),
                    shipped.locationId(),
                    item.qty(),
                    REF_TYPE,
                    row.getId(),
                    "Nhập lại hàng trả — " + row.getCode(),
                    actorId));
        }
    }

    private void applyReceiveLines(SalesReturn row, List<ReturnInspectLine> inspections, Long actorId) {
        Map<Long, Integer> remaining = new HashMap<>();
        for (ReturnItem item : row.getItems()) {
            remaining.merge(item.variantId(), item.qty(), Integer::sum);
        }
        for (ReturnInspectLine line : inspections) {
            if (line.qty() <= 0) {
                continue;
            }
            int left = remaining.getOrDefault(line.variantId(), 0);
            if (line.qty() > left) {
                throw ApiException.conflict("Số lượng nhận vượt quá phiếu trả");
            }
            remaining.put(line.variantId(), left - line.qty());

            StockTarget target = resolveStockTarget(
                    row.getOrder().getId(), line.variantId(), line.lotId(), line.locationId());
            stockService.receiveReturn(StockMutation.of(
                    target.variantId(),
                    target.lotId(),
                    target.locationId(),
                    line.qty(),
                    REF_TYPE,
                    row.getId(),
                    "Nhập lại hàng trả — " + row.getCode(),
                    actorId));
        }
        if (remaining.values().stream().anyMatch(q -> q > 0)) {
            throw ApiException.badRequest("Chưa nhận đủ số lượng trên phiếu trả");
        }
    }

    private StockTarget resolveStockTarget(Long orderId, Long variantId, Long lotId, Long locationId) {
        if (lotId != null && locationId != null) {
            return new StockTarget(variantId, lotId, locationId);
        }
        // Ưu tiên lô/vị trí từng xuất hoặc hold theo đơn.
        if (orderId != null) {
            List<StockLedger> ledgers =
                    stockLedgerRepository.findByRefTypeAndRefIdWithDetails(OrderAllocationService.REF_TYPE, orderId);
            for (int i = ledgers.size() - 1; i >= 0; i--) {
                StockLedger e = ledgers.get(i);
                if (e.getVariant() == null
                        || !Objects.equals(e.getVariant().getId(), variantId)
                        || e.getLot() == null
                        || e.getLocation() == null) {
                    continue;
                }
                if (e.getEntryType() != LedgerEntryType.OUT
                        && e.getEntryType() != LedgerEntryType.HOLD
                        && e.getEntryType() != LedgerEntryType.RELEASE) {
                    continue;
                }
                long resolvedLot = lotId != null ? lotId : e.getLot().getId();
                long resolvedLoc = locationId != null ? locationId : e.getLocation().getId();
                return new StockTarget(variantId, resolvedLot, resolvedLoc);
            }
        }
        List<Stock> stocks = stockRepository.findByVariantIdWithLotLocation(variantId);
        if (!stocks.isEmpty()) {
            Stock s = stocks.get(0);
            return new StockTarget(
                    variantId,
                    lotId != null ? lotId : s.getLot().getId(),
                    locationId != null ? locationId : s.getLocation().getId());
        }
        throw ApiException.badRequest(
                "Không tìm thấy lô/vị trí kho cho hàng trả — vui lòng chọn vị trí khi nhận hàng");
    }

    private Location requireLocation(long locationId) {
        return locationRepository
                .findById(locationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
    }

    private static String locationLabel(Location location) {
        if (location == null) {
            return null;
        }
        String wh = location.getWarehouse() == null ? null : Strings.trimToNull(location.getWarehouse().getCode());
        String zone = Strings.trimToNull(location.getZone());
        String code = wh == null ? location.getCode() : wh + "/" + location.getCode();
        return zone == null ? code : code + " · " + zone;
    }

    private record StockTarget(long variantId, long lotId, long locationId) {}

    private List<ReturnItem> buildItems(
            List<ReturnLineInput> inputs, Map<Long, Integer> maxByVariant, SalesOrder order) {
        Map<Long, SalesOrderLine> lineByVariant = order.getLines().stream()
                .collect(Collectors.toMap(l -> l.getVariant().getId(), Function.identity(), (a, b) -> a));

        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (ReturnLineInput input : inputs) {
            if (input.qty() <= 0) {
                continue;
            }
            requested.merge(input.variantId(), input.qty(), Integer::sum);
        }

        List<ReturnItem> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requested.entrySet()) {
            Long variantId = entry.getKey();
            int qty = entry.getValue();
            Integer max = maxByVariant.get(variantId);
            if (max == null || qty > max) {
                throw ApiException.conflict("Số lượng trả vượt quá số đã mua");
            }
            SalesOrderLine line = lineByVariant.get(variantId);
            if (line == null) {
                throw ApiException.badRequest("Sản phẩm không thuộc đơn đã chọn");
            }
            Long lotId = inputs.stream()
                    .filter(i -> Objects.equals(i.variantId(), variantId) && i.lotId() != null)
                    .map(ReturnLineInput::lotId)
                    .findFirst()
                    .orElse(null);
            BigDecimal amount = line.getUnitPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(2, RoundingMode.HALF_UP);
            items.add(new ReturnItem(variantId, lotId, qty, amount));
        }
        return items;
    }

    private Map<Long, Integer> maxQtyForCustomerReturn(Long orderId) {
        Map<Long, Integer> returned = returnedQtyByVariant(orderId, null);
        Map<Long, Integer> max = new HashMap<>();
        for (SalesOrderLine line : salesOrderLineRepository.findByOrder_IdOrderByIdAsc(orderId)) {
            Long variantId = line.getVariant().getId();
            int remain = line.getQtyKhay() - returned.getOrDefault(variantId, 0);
            if (remain > 0) {
                max.put(variantId, remain);
            }
        }
        return max;
    }

    private Map<Long, Integer> maxQtyForFailedDelivery(Delivery delivery) {
        Map<Long, Integer> returned = returnedQtyByVariant(delivery.getOrder().getId(), delivery.getId());
        Map<Long, SalesOrderLine> lineById = salesOrderLineRepository
                .findByOrder_IdOrderByIdAsc(delivery.getOrder().getId())
                .stream()
                .collect(Collectors.toMap(SalesOrderLine::getId, Function.identity()));

        Map<Long, Integer> max = new HashMap<>();
        for (DeliveryItem item : delivery.getItems()) {
            SalesOrderLine line = lineById.get(item.orderLineId());
            if (line == null) {
                continue;
            }
            Long variantId = line.getVariant().getId();
            int remain = item.qtyKhay() - returned.getOrDefault(variantId, 0);
            if (remain > 0) {
                max.merge(variantId, remain, Integer::sum);
            }
        }
        return max;
    }

    private Map<Long, Integer> returnedQtyByVariant(Long orderId, Long deliveryId) {
        List<SalesReturn> rows = deliveryId == null
                ? salesReturnRepository.findByOrder_IdAndStatusNot(orderId, ReturnStatus.REJECTED)
                : salesReturnRepository.findByDelivery_IdAndStatusNot(deliveryId, ReturnStatus.REJECTED);
        Map<Long, Integer> qty = new HashMap<>();
        for (SalesReturn row : rows) {
            if (deliveryId == null && row.getDelivery() != null) {
                // Phiếu nhận về từ đợt thất bại không trừ hạn mức trả của khách sau khi nhận.
                continue;
            }
            for (ReturnItem item : row.getItems()) {
                qty.merge(item.variantId(), item.qty(), Integer::sum);
            }
        }
        return qty;
    }

    private List<ReturnableLineResponse> returnableLinesForOrder(Long orderId) {
        SalesOrder order = salesOrderRepository.findWithDetailsById(orderId).orElse(null);
        if (order == null) {
            return List.of();
        }
        Map<Long, Integer> max = maxQtyForCustomerReturn(orderId);
        List<ReturnableLineResponse> lines = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            Long variantId = line.getVariant().getId();
            Integer maxQty = max.get(variantId);
            if (maxQty == null || maxQty <= 0) {
                continue;
            }
            ProductVariant variant = line.getVariant();
            String productName = variant.getProduct() == null ? variant.getSku() : variant.getProduct().getName();
            lines.add(new ReturnableLineResponse(
                    variantId,
                    variant.getSku(),
                    productName,
                    variant.getPackLabel(),
                    line.getUnitPrice(),
                    maxQty));
        }
        return lines;
    }

    private ReturnListItemResponse toListItem(SalesReturn row) {
        Set<Long> variantIds = row.getItems().stream().map(ReturnItem::variantId).collect(Collectors.toSet());
        Map<Long, ProductVariant> variants = productVariantRepository.findAllById(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        // Ensure product name is loaded
        variants.values().forEach(v -> {
            if (v.getProduct() != null) {
                v.getProduct().getName();
            }
        });

        List<ReturnItemViewResponse> items = row.getItems().stream()
                .map(item -> {
                    ProductVariant variant = variants.get(item.variantId());
                    String sku = variant == null ? null : variant.getSku();
                    String productName = variant == null || variant.getProduct() == null
                            ? sku
                            : variant.getProduct().getName();
                    return ReturnItemViewResponse.of(item, sku, productName);
                })
                .toList();
        return ReturnListItemResponse.from(row, items);
    }

    private String nextCode() {
        String year = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));
        String prefix = "TH-" + year + "-";
        int next = salesReturnRepository.findCodesByPrefix(prefix).stream()
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

    private void addEvent(SalesOrder order, String type, String description, AppUser actor) {
        OrderEvent event = new OrderEvent();
        event.setOrder(order);
        event.setEventType(type);
        event.setDescription(description);
        event.setCreatedBy(actor);
        orderEventRepository.save(event);
    }

    private static ReturnStatus parseStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return ReturnStatus.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
    }
}
