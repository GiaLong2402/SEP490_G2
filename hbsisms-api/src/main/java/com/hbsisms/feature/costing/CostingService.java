package com.hbsisms.feature.costing;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.costing.dto.CostItemBalanceResponse;
import com.hbsisms.feature.costing.dto.CostPeriodResponse;
import com.hbsisms.feature.costing.dto.CostRunningBalanceResponse;
import com.hbsisms.feature.costing.dto.DiscardCostPeriodRequest;
import com.hbsisms.feature.costing.dto.OpenCostPeriodRequest;
import com.hbsisms.feature.costing.dto.ReopenCostPeriodRequest;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.system.AuditLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 55 — mở kỳ; task 57 — khóa sổ; task 58 — mở lại kỳ đã khóa / hủy kỳ OPEN nhầm.
 * Task 56 ghi nhận nhập/xuất vào số dư kỳ mở.
 */
@Service
@Transactional(readOnly = true)
public class CostingService {

    public static final String REF_TYPE = "cost_period";

    private final CostPeriodRepository periodRepository;
    private final CostItemBalanceRepository balanceRepository;
    private final CostRunningBalanceRepository runningBalanceRepository;
    private final CostRunningWacService runningWacService;
    private final RawMaterialLotRepository rawMaterialLotRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final StockRepository stockRepository;
    private final ProductVariantRepository productVariantRepository;
    private final AuditLogService auditLogService;

    public CostingService(
            CostPeriodRepository periodRepository,
            CostItemBalanceRepository balanceRepository,
            CostRunningBalanceRepository runningBalanceRepository,
            CostRunningWacService runningWacService,
            RawMaterialLotRepository rawMaterialLotRepository,
            RawMaterialRepository rawMaterialRepository,
            StockRepository stockRepository,
            ProductVariantRepository productVariantRepository,
            AuditLogService auditLogService) {
        this.periodRepository = periodRepository;
        this.balanceRepository = balanceRepository;
        this.runningBalanceRepository = runningBalanceRepository;
        this.runningWacService = runningWacService;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.stockRepository = stockRepository;
        this.productVariantRepository = productVariantRepository;
        this.auditLogService = auditLogService;
    }

    public List<CostPeriodResponse> listPeriods() {
        return periodRepository.findAllByOrderByPeriodStartDesc().stream()
                .map(p -> CostPeriodResponse.of(p, balanceRepository.countByPeriod_Id(p.getId())))
                .toList();
    }

    public Optional<CostPeriodResponse> findOpenPeriod() {
        return periodRepository
                .findByStatus(CostPeriodStatus.OPEN)
                .map(p -> CostPeriodResponse.of(p, balanceRepository.countByPeriod_Id(p.getId())));
    }

    public CostPeriodResponse getPeriod(Long id) {
        CostPeriod period = requirePeriod(id);
        return CostPeriodResponse.of(period, balanceRepository.countByPeriod_Id(period.getId()));
    }

    public List<CostItemBalanceResponse> listBalances(Long periodId, CostItemType itemType) {
        requirePeriod(periodId);
        List<CostItemBalance> rows = itemType == null
                ? balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(periodId)
                : balanceRepository.findByPeriod_IdAndItemTypeOrderByItemIdAsc(periodId, itemType);
        return enrichBalances(rows);
    }

    /** Bảng WAC liên hoàn hiện hành (NVL + TP), không phụ thuộc kỳ. */
    public List<CostRunningBalanceResponse> listRunningBalances(CostItemType itemType) {
        List<CostRunningBalance> rows = itemType == null
                ? runningBalanceRepository.findAllByOrderByItemTypeAscItemIdAsc()
                : runningBalanceRepository.findByItemTypeOrderByItemIdAsc(itemType);
        return enrichRunningBalances(rows);
    }

    private List<CostItemBalanceResponse> enrichBalances(List<CostItemBalance> rows) {
        ItemLabels labels = resolveItemLabels(rows, CostItemBalance::getItemType, CostItemBalance::getItemId);
        List<CostItemBalanceResponse> out = new ArrayList<>(rows.size());
        for (CostItemBalance row : rows) {
            String[] cn = labels.codeName(row.getItemType(), row.getItemId());
            out.add(CostItemBalanceResponse.of(row, cn[0], cn[1]));
        }
        return out;
    }

    private List<CostRunningBalanceResponse> enrichRunningBalances(List<CostRunningBalance> rows) {
        ItemLabels labels = resolveItemLabels(rows, CostRunningBalance::getItemType, CostRunningBalance::getItemId);
        List<CostRunningBalanceResponse> out = new ArrayList<>(rows.size());
        for (CostRunningBalance row : rows) {
            String[] cn = labels.codeName(row.getItemType(), row.getItemId());
            out.add(CostRunningBalanceResponse.of(row, cn[0], cn[1], labels.unit(row.getItemType(), row.getItemId())));
        }
        return out;
    }

    private <T> ItemLabels resolveItemLabels(
            List<T> rows,
            Function<T, CostItemType> typeFn,
            Function<T, Long> idFn) {
        Set<Long> rawIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>();
        for (T row : rows) {
            if (typeFn.apply(row) == CostItemType.RAW_MATERIAL) {
                rawIds.add(idFn.apply(row));
            } else {
                variantIds.add(idFn.apply(row));
            }
        }
        Map<Long, RawMaterial> rawById = rawIds.isEmpty()
                ? Map.of()
                : rawMaterialRepository.findAllById(rawIds).stream()
                        .collect(Collectors.toMap(RawMaterial::getId, Function.identity()));
        Map<Long, ProductVariant> variantById = variantIds.isEmpty()
                ? Map.of()
                : productVariantRepository.findWithProductByIdIn(variantIds).stream()
                        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        return new ItemLabels(rawById, variantById);
    }

    private record ItemLabels(Map<Long, RawMaterial> rawById, Map<Long, ProductVariant> variantById) {
        String[] codeName(CostItemType type, Long itemId) {
            if (type == CostItemType.RAW_MATERIAL) {
                RawMaterial m = rawById.get(itemId);
                return m == null
                        ? new String[] {"#" + itemId, "NVL đã xóa"}
                        : new String[] {m.getCode(), m.getName()};
            }
            ProductVariant v = variantById.get(itemId);
            if (v == null) {
                return new String[] {"#" + itemId, "SKU đã xóa"};
            }
            return new String[] {v.getSku(), v.getProduct().getName() + " · " + v.getPackLabel()};
        }

        /** NVL theo đơn vị danh mục; TP theo khay vật lý trên sổ tồn. */
        String unit(CostItemType type, Long itemId) {
            if (type == CostItemType.RAW_MATERIAL) {
                RawMaterial m = rawById.get(itemId);
                if (m == null) {
                    return "";
                }
                String u = m.getUnit();
                return u == null ? "" : u;
            }
            return "khay";
        }
    }

    /**
     * Mở kỳ mới: chỉ khi chưa có kỳ OPEN; seed tồn đầu từ kho hiện tại.
     * Nếu đã có kỳ tháng trước đã khóa → lấy tồn cuối làm tồn đầu (task 57 sẽ ghi sẵn);
     * kỳ đầu tiên / chưa có kỳ trước → snapshot tồn vật lý.
     */
    @Transactional
    public CostPeriodResponse openPeriod(OpenCostPeriodRequest request, Long actorUserId) {
        YearMonth yearMonth = parseYearMonth(request.yearMonth());
        LocalDate periodStart = yearMonth.atDay(1);

        if (periodRepository.existsByStatus(CostPeriodStatus.OPEN)) {
            throw ApiException.conflict("Đang có kỳ giá vốn mở — khóa sổ trước khi mở kỳ mới");
        }
        if (periodRepository.findByPeriodStart(periodStart).isPresent()) {
            throw ApiException.conflict("Kỳ " + yearMonth + " đã tồn tại");
        }

        CostPeriod period = new CostPeriod();
        period.setPeriodStart(periodStart);
        period.setStatus(CostPeriodStatus.OPEN);
        period.setOpenedAt(Instant.now());
        period.setOpenedByUserId(actorUserId);
        period = periodRepository.save(period);

        List<CostItemBalance> balances = seedOpeningBalances(period, yearMonth);
        if (!balances.isEmpty()) {
            balanceRepository.saveAll(balances);
        }

        return CostPeriodResponse.of(period, balances.size());
    }

    /**
     * Task 57 — Chủ khóa sổ: tính ĐG bình quân chính thức theo thứ tự NVL → thành phẩm,
     * chốt giá trị xuất / tồn cuối, đánh dấu CLOSED. Tồn cuối dùng khi mở kỳ sau (carry-forward).
     */
    @Transactional
    public CostPeriodResponse closePeriod(Long periodId, Long actorUserId) {
        CostPeriod period = requirePeriod(periodId);
        if (period.getStatus() != CostPeriodStatus.OPEN) {
            throw ApiException.conflict("Kỳ đã khóa — không khóa lại được");
        }

        List<CostItemBalance> rows =
                balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(period.getId());
        Map<String, BigDecimal> prevAvg = previousClosedAvgMap(YearMonth.from(period.getPeriodStart()));

        // NVL trước, rồi thành phẩm (đúng thứ tự Inter 1 task 57).
        rows.stream()
                .sorted(Comparator.comparing((CostItemBalance r) -> r.getItemType() == CostItemType.RAW_MATERIAL ? 0 : 1)
                        .thenComparing(CostItemBalance::getItemId))
                .forEach(row -> {
                    try {
                        row.finalizeForClose(prevAvg.get(avgKey(row.getItemType(), row.getItemId())));
                    } catch (IllegalStateException ex) {
                        throw ApiException.conflict(ex.getMessage());
                    }
                });
        balanceRepository.saveAll(rows);

        period.setStatus(CostPeriodStatus.CLOSED);
        period.setClosedAt(Instant.now());
        period.setClosedByUserId(actorUserId);
        periodRepository.save(period);

        YearMonth ym = YearMonth.from(period.getPeriodStart());
        auditLogService.record(
                actorUserId,
                "close",
                REF_TYPE,
                period.getId(),
                Map.of(
                        "yearMonth", ym.toString(),
                        "balanceCount", rows.size()));

        return CostPeriodResponse.of(period, rows.size());
    }

    /**
     * Task 58 — mở lại kỳ đã khóa để ghi thêm nhập/xuất.
     * Điều kiện: kỳ CLOSED; không còn kỳ OPEN; không còn kỳ tháng sau (tránh lệch sổ).
     */
    @Transactional
    public CostPeriodResponse reopenPeriod(Long periodId, ReopenCostPeriodRequest request, Long actorUserId) {
        CostPeriod period = requirePeriod(periodId);
        if (period.getStatus() != CostPeriodStatus.CLOSED) {
            throw ApiException.conflict("Chỉ mở lại được kỳ đã khóa");
        }
        if (periodRepository.existsByStatus(CostPeriodStatus.OPEN)) {
            throw ApiException.conflict("Đang có kỳ giá vốn mở — khóa hoặc hủy kỳ đó trước khi mở lại");
        }
        if (periodRepository.existsByPeriodStartAfter(period.getPeriodStart())) {
            YearMonth later = periodRepository
                    .findFirstByOrderByPeriodStartDesc()
                    .map(p -> YearMonth.from(p.getPeriodStart()))
                    .orElse(null);
            throw ApiException.conflict(
                    "Có kỳ sau ("
                            + later
                            + ") — hủy kỳ mở nhầm / xử lý kỳ sau trước khi mở lại "
                            + YearMonth.from(period.getPeriodStart()));
        }

        String reason = request.reason().trim();
        period.setStatus(CostPeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedByUserId(null);
        period.setReopenReason(reason);
        period.setOpenedAt(Instant.now());
        period.setOpenedByUserId(actorUserId);
        periodRepository.save(period);

        List<CostItemBalance> rows =
                balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(period.getId());
        for (CostItemBalance row : rows) {
            row.refreshDerived();
        }
        balanceRepository.saveAll(rows);

        YearMonth ym = YearMonth.from(period.getPeriodStart());
        auditLogService.record(
                actorUserId,
                "reopen",
                REF_TYPE,
                period.getId(),
                Map.of("yearMonth", ym.toString(), "reason", reason, "balanceCount", rows.size()));

        return CostPeriodResponse.of(period, rows.size());
    }

    /**
     * Hủy kỳ OPEN mở nhầm (vd. mở 12 khi vẫn làm việc tháng 11). Chỉ kỳ mới nhất; xóa luôn số dư.
     */
    @Transactional
    public void discardOpenPeriod(Long periodId, DiscardCostPeriodRequest request, Long actorUserId) {
        CostPeriod period = requirePeriod(periodId);
        if (period.getStatus() != CostPeriodStatus.OPEN) {
            throw ApiException.conflict("Chỉ hủy được kỳ đang mở");
        }
        Optional<CostPeriod> latest = periodRepository.findFirstByOrderByPeriodStartDesc();
        if (latest.isEmpty() || !latest.get().getId().equals(period.getId())) {
            throw ApiException.conflict("Chỉ hủy được kỳ tháng mới nhất đang mở");
        }

        String reason = request.reason().trim();
        YearMonth ym = YearMonth.from(period.getPeriodStart());
        long balanceCount = balanceRepository.countByPeriod_Id(period.getId());
        periodRepository.delete(period);

        auditLogService.record(
                actorUserId,
                "discard",
                REF_TYPE,
                periodId,
                Map.of("yearMonth", ym.toString(), "reason", reason, "balanceCount", balanceCount));
    }

    /** Task 56 gọi trước khi cộng nhập/xuất vào số dư. */
    public CostPeriod requireOpenPeriod() {
        return periodRepository
                .findByStatus(CostPeriodStatus.OPEN)
                .orElseThrow(() -> ApiException.conflict("Chưa có kỳ giá vốn đang mở"));
    }

    /**
     * Task 59 — ĐG bình quân TP hiện hành từ WAC liên hoàn (số dư chạy; không phụ thuộc kỳ OPEN).
     */
    public Optional<BigDecimal> currentFinishedAvgUnitCost(Long variantId) {
        return currentAvgUnitCost(CostItemType.FINISHED, variantId);
    }

    /**
     * WAC liên hoàn NVL hiện hành (số dư chạy sau mỗi nhập/xuất).
     */
    public Optional<BigDecimal> currentRawAvgUnitCost(Long rawMaterialId) {
        return currentAvgUnitCost(CostItemType.RAW_MATERIAL, rawMaterialId);
    }

    /**
     * Đơn giá xuất NVL cho SX: ưu tiên WAC liên hoàn; chưa có WAC → giá lô (fallback).
     */
    public BigDecimal resolveRawIssueUnitCost(Long rawMaterialId, BigDecimal lotUnitPrice) {
        return currentRawAvgUnitCost(rawMaterialId)
                .orElseGet(() -> lotUnitPrice == null ? BigDecimal.ZERO : lotUnitPrice);
    }

    private Optional<BigDecimal> currentAvgUnitCost(CostItemType itemType, Long itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        Optional<BigDecimal> fromRunning = runningWacService.currentAvgUnitCost(itemType, itemId);
        if (fromRunning.isPresent()) {
            return fromRunning;
        }
        // Fallback một lần từ sổ kỳ (migrate), rồi ghi vào running nếu có.
        Optional<CostItemBalance> fromPeriod = Optional.empty();
        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isPresent()) {
            fromPeriod = balanceRepository.findByPeriod_IdAndItemTypeAndItemId(
                    open.get().getId(), itemType, itemId);
        }
        if (fromPeriod.isEmpty()) {
            fromPeriod = periodRepository
                    .findFirstByStatusOrderByPeriodStartDesc(CostPeriodStatus.CLOSED)
                    .flatMap(p -> balanceRepository.findByPeriod_IdAndItemTypeAndItemId(
                            p.getId(), itemType, itemId));
        }
        if (fromPeriod.isPresent()) {
            CostItemBalance bal = fromPeriod.get();
            BigDecimal avg = bal.getAvgUnitCost();
            if (avg != null && avg.signum() > 0) {
                runningWacService.ensurePresent(
                        itemType,
                        itemId,
                        bal.getClosingQty() != null ? bal.getClosingQty() : BigDecimal.ZERO,
                        bal.getClosingValue() != null ? bal.getClosingValue() : BigDecimal.ZERO);
                return Optional.of(avg);
            }
        }
        return Optional.empty();
    }

    public void assertPeriodMutable(CostPeriod period) {
        if (period.getStatus() == CostPeriodStatus.CLOSED) {
            throw ApiException.conflict("Kỳ đã khóa — không sửa phiếu; điều chỉnh ở kỳ sau");
        }
    }

    private Map<String, BigDecimal> previousClosedAvgMap(YearMonth current) {
        Optional<CostPeriod> prev =
                periodRepository.findByPeriodStart(current.minusMonths(1).atDay(1));
        if (prev.isEmpty() || prev.get().getStatus() != CostPeriodStatus.CLOSED) {
            return Map.of();
        }
        Map<String, BigDecimal> map = new HashMap<>();
        for (CostItemBalance row :
                balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(prev.get().getId())) {
            if (row.getAvgUnitCost() != null) {
                map.put(avgKey(row.getItemType(), row.getItemId()), row.getAvgUnitCost());
            }
        }
        return map;
    }

    private static String avgKey(CostItemType type, Long itemId) {
        return type.name() + ":" + itemId;
    }

    /**
     * Ưu tiên seed từ WAC chạy; không có → carry-forward / snapshot lô, rồi đảm bảo running có dòng.
     */
    private List<CostItemBalance> seedOpeningBalances(CostPeriod period, YearMonth yearMonth) {
        List<CostRunningBalance> running = runningWacService.listWithStock();
        if (!running.isEmpty()) {
            List<CostItemBalance> fromRunning = new ArrayList<>(running.size());
            for (CostRunningBalance r : running) {
                fromRunning.add(CostItemBalance.opening(
                        period, r.getItemType(), r.getItemId(), r.getQtyOnHand(), r.getInventoryValue()));
            }
            return fromRunning;
        }

        Optional<CostPeriod> previous = periodRepository.findByPeriodStart(yearMonth.minusMonths(1).atDay(1));
        List<CostItemBalance> seeded;
        if (previous.isPresent() && previous.get().getStatus() == CostPeriodStatus.CLOSED) {
            seeded = carryForward(period, previous.get());
        } else {
            seeded = snapshotPhysicalStock(period);
        }
        for (CostItemBalance row : seeded) {
            runningWacService.ensurePresent(
                    row.getItemType(), row.getItemId(), row.getOpeningQty(), row.getOpeningValue());
        }
        return seeded;
    }

    /** Tồn cuối kỳ trước → tồn đầu kỳ mới (sau khóa sổ). */
    private List<CostItemBalance> carryForward(CostPeriod period, CostPeriod previous) {
        List<CostItemBalance> prevRows =
                balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(previous.getId());
        List<CostItemBalance> next = new ArrayList<>(prevRows.size());
        for (CostItemBalance prev : prevRows) {
            if (prev.getClosingQty().signum() == 0 && prev.getClosingValue().signum() == 0) {
                continue;
            }
            next.add(CostItemBalance.opening(
                    period, prev.getItemType(), prev.getItemId(), prev.getClosingQty(), prev.getClosingValue()));
        }
        return next;
    }

    /** Snapshot kho: NVL = Σ qtyRemaining × unitPrice; TP = Σ (available+held) × lot.unitCost. */
    private List<CostItemBalance> snapshotPhysicalStock(CostPeriod period) {
        Map<Long, QtyValue> raw = new HashMap<>();
        for (RawMaterialLot lot : rawMaterialLotRepository.findAllWithRemaining()) {
            if (lot.getQtyRemaining() == null || lot.getQtyRemaining().signum() <= 0) {
                continue;
            }
            BigDecimal price = lot.getUnitPrice() == null ? BigDecimal.ZERO : lot.getUnitPrice();
            BigDecimal value = lot.getQtyRemaining().multiply(price).setScale(2, RoundingMode.HALF_UP);
            raw.merge(
                    lot.getRawMaterial().getId(),
                    new QtyValue(lot.getQtyRemaining(), value),
                    QtyValue::add);
        }

        Map<Long, QtyValue> finished = new HashMap<>();
        for (Stock stock : stockRepository.findAllWithDetails()) {
            int qty = stock.getQtyAvailable() + stock.getQtyHeld();
            if (qty <= 0) {
                continue;
            }
            BigDecimal unitCost =
                    stock.getLot().getUnitCost() == null ? BigDecimal.ZERO : stock.getLot().getUnitCost();
            BigDecimal q = BigDecimal.valueOf(qty);
            BigDecimal value = q.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            finished.merge(stock.getVariant().getId(), new QtyValue(q, value), QtyValue::add);
        }

        List<CostItemBalance> rows = new ArrayList<>(raw.size() + finished.size());
        raw.forEach((id, qv) -> rows.add(
                CostItemBalance.opening(period, CostItemType.RAW_MATERIAL, id, qv.qty(), qv.value())));
        finished.forEach((id, qv) -> rows.add(
                CostItemBalance.opening(period, CostItemType.FINISHED, id, qv.qty(), qv.value())));
        return rows;
    }

    private CostPeriod requirePeriod(Long id) {
        return periodRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kỳ giá vốn"));
    }

    private static YearMonth parseYearMonth(String raw) {
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest("yearMonth phải dạng yyyy-MM");
        }
    }

    private record QtyValue(BigDecimal qty, BigDecimal value) {
        QtyValue add(QtyValue other) {
            return new QtyValue(qty.add(other.qty), value.add(other.value));
        }
    }
}
