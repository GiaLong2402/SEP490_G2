package com.hbsisms.feature.sales;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.LocationRepository;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.LotRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.OrderAllocationService.HoldKey;
import com.hbsisms.feature.sales.dto.DailyPickingGroupResponse;
import com.hbsisms.feature.sales.dto.DailyPickingLineResponse;
import com.hbsisms.feature.sales.dto.DailyPickingSlipResponse;
import com.hbsisms.feature.sales.dto.PickingSlipLineResponse;
import com.hbsisms.feature.sales.dto.PickingSlipResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phiếu soạn từ HOLD còn hiệu lực — theo đơn hoặc bản gộp theo ngày giao (FE Soạn hàng).
 */
@Service
@Transactional(readOnly = true)
public class PickingSlipService {

    private static final List<OrderState> DAILY_STATES =
            List.of(OrderState.CONFIRMED, OrderState.PREPARING, OrderState.TO_SHIP);

    private final SalesOrderRepository salesOrderRepository;
    private final DeliveryRepository deliveryRepository;
    private final OrderAllocationService orderAllocationService;
    private final ProductVariantRepository productVariantRepository;
    private final LotRepository lotRepository;
    private final LocationRepository locationRepository;
    private final Clock clock;

    public PickingSlipService(
            SalesOrderRepository salesOrderRepository,
            DeliveryRepository deliveryRepository,
            OrderAllocationService orderAllocationService,
            ProductVariantRepository productVariantRepository,
            LotRepository lotRepository,
            LocationRepository locationRepository,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.deliveryRepository = deliveryRepository;
        this.orderAllocationService = orderAllocationService;
        this.productVariantRepository = productVariantRepository;
        this.lotRepository = lotRepository;
        this.locationRepository = locationRepository;
        this.clock = clock;
    }

    public PickingSlipResponse forOrder(Long orderId) {
        SalesOrder order = salesOrderRepository
                .findWithHeaderById(orderId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn hàng"));
        Instant now = Instant.now(clock);
        String customerName = order.getCustomerName();
        if (customerName == null && order.getCustomer() != null) {
            customerName = order.getCustomer().getName();
        }
        return new PickingSlipResponse(
                order.getId(),
                order.getCode(),
                customerName,
                now,
                toSlipLines(orderAllocationService.netHeldByLot(order.getId())));
    }

    public DailyPickingSlipResponse daily(LocalDate date) {
        Objects.requireNonNull(date, "date");
        Instant now = Instant.now(clock);

        List<SalesOrder> candidates = salesOrderRepository.findForDailyPicking(
                DAILY_STATES, PaymentStatus.PAID, AllocationStatus.FULL);

        Map<Long, List<Delivery>> deliveriesByOrder = deliveryRepository
                .findByOrder_IdIn(candidates.stream().map(SalesOrder::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(d -> d.getOrder().getId()));

        List<SalesOrder> matched = candidates.stream()
                .filter(o -> matchesShipDate(deliveriesByOrder.getOrDefault(o.getId(), List.of()), date))
                .toList();

        Map<Long, MutableGroup> byLocation = new LinkedHashMap<>();
        for (SalesOrder order : matched) {
            for (PickingSlipLineResponse line :
                    toSlipLines(orderAllocationService.netHeldByLot(order.getId()))) {
                if (line.locationId() == null || line.qtyKhay() <= 0) {
                    continue;
                }
                MutableGroup group = byLocation.computeIfAbsent(line.locationId(), id -> new MutableGroup(
                        line.warehouseId(),
                        line.warehouseCode(),
                        line.locationId(),
                        line.locationCode(),
                        line.zone()));
                group.lines.add(new DailyPickingLineResponse(
                        order.getId(),
                        order.getCode(),
                        line.variantId(),
                        line.productName(),
                        line.sku(),
                        line.packLabel(),
                        line.lotId(),
                        line.lotCode(),
                        line.expDate(),
                        line.qtyKhay()));
                group.totalKhay += line.qtyKhay();
            }
        }

        List<DailyPickingGroupResponse> groups = byLocation.values().stream()
                .map(g -> {
                    g.lines.sort(Comparator.comparing(
                                    (DailyPickingLineResponse l) ->
                                            l.expDate() == null ? LocalDate.MAX : l.expDate())
                            .thenComparing(DailyPickingLineResponse::orderCode));
                    return new DailyPickingGroupResponse(
                            g.warehouseId,
                            g.warehouseCode,
                            g.locationId,
                            g.locationCode,
                            g.zone,
                            g.totalKhay,
                            List.copyOf(g.lines));
                })
                .sorted(Comparator.comparing(
                                (DailyPickingGroupResponse g) ->
                                        g.warehouseCode() == null ? "" : g.warehouseCode())
                        .thenComparing(g -> g.locationCode() == null ? "" : g.locationCode()))
                .toList();

        return new DailyPickingSlipResponse(date, now, groups);
    }

    private static boolean matchesShipDate(List<Delivery> deliveries, LocalDate date) {
        if (deliveries.isEmpty()) {
            return true;
        }
        return deliveries.stream().anyMatch(d -> date.equals(d.getScheduledDate()));
    }

    private List<PickingSlipLineResponse> toSlipLines(Map<HoldKey, Integer> holds) {
        if (holds.isEmpty()) {
            return List.of();
        }

        Set<Long> variantIds = new HashSet<>();
        Set<Long> lotIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        for (HoldKey key : holds.keySet()) {
            variantIds.add(key.variantId());
            lotIds.add(key.lotId());
            locationIds.add(key.locationId());
        }

        Map<Long, ProductVariant> variants = productVariantRepository.findWithProductByIdIn(variantIds).stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        Map<Long, Lot> lots = lotRepository.findAllById(lotIds).stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));
        Map<Long, Location> locations = locationRepository.findWithWarehouseByIdIn(locationIds).stream()
                .collect(Collectors.toMap(Location::getId, Function.identity(), (a, b) -> a, HashMap::new));

        List<PickingSlipLineResponse> lines = new ArrayList<>();
        for (Map.Entry<HoldKey, Integer> entry : holds.entrySet()) {
            int qty = entry.getValue() == null ? 0 : entry.getValue();
            if (qty <= 0) {
                continue;
            }
            HoldKey key = entry.getKey();
            ProductVariant variant = variants.get(key.variantId());
            Lot lot = lots.get(key.lotId());
            Location location = locations.get(key.locationId());
            String productName = variant != null && variant.getProduct() != null
                    ? variant.getProduct().getName()
                    : null;
            String sku = variant != null ? variant.getSku() : "—";
            String packLabel = variant != null ? variant.getPackLabel() : null;
            Long warehouseId = location != null && location.getWarehouse() != null
                    ? location.getWarehouse().getId()
                    : null;
            String warehouseCode = location != null && location.getWarehouse() != null
                    ? location.getWarehouse().getCode()
                    : null;
            lines.add(new PickingSlipLineResponse(
                    key.variantId(),
                    productName,
                    sku,
                    packLabel,
                    key.lotId(),
                    lot != null ? lot.getLotCode() : null,
                    lot != null ? lot.getExpDate() : null,
                    warehouseId,
                    warehouseCode,
                    key.locationId(),
                    location != null ? location.getCode() : null,
                    location != null ? location.getZone() : null,
                    qty));
        }

        lines.sort(Comparator.comparing(
                        (PickingSlipLineResponse l) -> l.expDate() == null ? LocalDate.MAX : l.expDate())
                .thenComparing(l -> l.warehouseCode() == null ? "" : l.warehouseCode())
                .thenComparing(l -> l.locationCode() == null ? "" : l.locationCode()));
        return lines;
    }

    private static final class MutableGroup {
        private final Long warehouseId;
        private final String warehouseCode;
        private final Long locationId;
        private final String locationCode;
        private final String zone;
        private int totalKhay;
        private final List<DailyPickingLineResponse> lines = new ArrayList<>();

        private MutableGroup(
                Long warehouseId,
                String warehouseCode,
                Long locationId,
                String locationCode,
                String zone) {
            this.warehouseId = warehouseId;
            this.warehouseCode = warehouseCode;
            this.locationId = locationId;
            this.locationCode = locationCode;
            this.zone = zone;
        }
    }
}
