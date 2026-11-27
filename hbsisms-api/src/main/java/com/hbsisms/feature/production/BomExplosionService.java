package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.costing.CostingService;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc BOM × SL → snapshot NVL + tồn dùng được + chi phí tạm (WAC).
 *
 * <p>BOM ghi cho <b>1 kg thành phẩm</b>. Kế hoạch / xuất đầu lệnh chỉ lấy dòng <b>theo kg</b>
 * ({@code plannedQty} = số khay quy đổi 1 kg). Dòng đếm cái (khay xốp, màng…) trừ lúc đóng gói theo
 * số khay vật lý ({@link #explodePackaging}).
 */
@Service
@Transactional(readOnly = true)
public class BomExplosionService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int QTY_SCALE = 4;
    private static final int MONEY_SCALE = 2;
    /** Đơn vị khối lượng / thể tích — xuất lúc bắt đầu SX. Còn lại (cái, cuộn…) = VT đóng gói. */
    private static final Set<String> WEIGHT_UNITS = Set.of("kg", "g", "gram", "l", "lit", "ml");

    private final ProductBomLineRepository bomLineRepository;
    private final RawMaterialLotRepository rawMaterialLotRepository;
    private final CostingService costingService;
    private final Clock clock;

    public BomExplosionService(
            ProductBomLineRepository bomLineRepository,
            RawMaterialLotRepository rawMaterialLotRepository,
            CostingService costingService,
            Clock clock) {
        this.bomLineRepository = bomLineRepository;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
        this.costingService = costingService;
        this.clock = clock;
    }

    /**
     * NVL theo kg cho kế hoạch / xuất đầu lệnh. {@code plannedQty} = khay quy đổi 1 kg.
     *
     * <p>{@code noRollbackFor}: caller có thể catch khi thiếu BOM mà không làm TX ngoài rollback-only.
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ExplosionResult explode(Long productId, BigDecimal plannedQty) {
        return explodeLines(productId, plannedQty, LineKind.WEIGHT);
    }

    /**
     * @deprecated Quy cách không còn chọn lúc lập kế hoạch — dùng {@link #explode(Long, BigDecimal)}.
     *     Giữ để tương thích call site cũ; bỏ qua {@code variant}.
     */
    @Deprecated
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ExplosionResult explode(Long productId, BigDecimal plannedQty, ProductVariant variant) {
        return explode(productId, plannedQty);
    }

    /**
     * VT đóng gói (khay xốp, màng…) theo số khay vật lý lúc hoàn thành / đóng gói.
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ExplosionResult explodePackaging(Long productId, BigDecimal physicalTrayCount) {
        return explodeLines(productId, physicalTrayCount, LineKind.COUNT);
    }

    private ExplosionResult explodeLines(Long productId, BigDecimal qty, LineKind kind) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest(
                    kind == LineKind.COUNT
                            ? "Số khay đóng gói phải > 0"
                            : "Số lượng kế hoạch phải > 0");
        }
        List<ProductBomLine> bomLines = bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId);
        if (bomLines.isEmpty()) {
            throw ApiException.badRequest("Sản phẩm chưa có định mức nguyên liệu (BOM)");
        }

        LocalDate today = LocalDate.now(clock.withZone(VN));
        List<ProductionOrderMaterialLine> lines = new ArrayList<>();
        List<BigDecimal> expiredQtys = new ArrayList<>();
        BigDecimal materialCost = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        for (ProductBomLine bom : bomLines) {
            if (bom.getComponentProduct() != null) {
                throw ApiException.badRequest(
                        "BOM chứa sản phẩm cấu thành — hiện chỉ hỗ trợ nguyên liệu thô");
            }
            RawMaterial raw = bom.getRawMaterial();
            if (raw == null) {
                throw ApiException.badRequest("Dòng BOM thiếu nguyên liệu");
            }

            boolean weight = isWeightLine(bom, raw);
            if (kind == LineKind.WEIGHT && !weight) {
                continue;
            }
            if (kind == LineKind.COUNT && weight) {
                continue;
            }

            // Kế hoạch: định mức / kg × số khay 1 kg quy đổi (+ % hao). Đóng gói: định mức × khay vật lý.
            BigDecimal perUnit = bom.getQty();
            BigDecimal waste = bom.getWastePercent() == null ? BigDecimal.ZERO : bom.getWastePercent();
            if (waste.compareTo(HUNDRED) >= 0) {
                throw ApiException.badRequest("Hao hụt của " + raw.getName() + " phải nhỏ hơn 100%");
            }
            BigDecimal base = perUnit.multiply(qty);
            BigDecimal required;
            if (kind == LineKind.COUNT) {
                // VT cái/cuộn: trừ số nguyên (1 túi/khay…); không nhân % hao thành thập phân kiểu 2,020.
                required = base.setScale(0, RoundingMode.CEILING);
            } else {
                BigDecimal keep = BigDecimal.ONE.subtract(waste.divide(HUNDRED, 8, RoundingMode.HALF_UP));
                required = base.divide(keep, QTY_SCALE, RoundingMode.HALF_UP);
            }

            BigDecimal usable = rawMaterialLotRepository.sumUsableRemaining(raw.getId(), today);
            if (usable == null) {
                usable = BigDecimal.ZERO;
            }
            BigDecimal expired = rawMaterialLotRepository.sumExpiredRemaining(raw.getId(), today);
            if (expired == null) {
                expired = BigDecimal.ZERO;
            }
            boolean sufficient = usable.compareTo(required) >= 0;
            BigDecimal lineCost = estimateMaterialCost(raw.getId(), required, today);

            ProductionOrderMaterialLine line = new ProductionOrderMaterialLine();
            line.setRawMaterial(raw);
            line.setBomQtyPerUnit(perUnit);
            line.setWastePercent(bom.getWastePercent());
            line.setRequiredQty(required);
            line.setOnHandQty(usable);
            line.setSufficient(sufficient);
            line.setLineEstimatedCost(lineCost);
            lines.add(line);
            expiredQtys.add(expired);
            materialCost = materialCost.add(lineCost);
        }

        return new ExplosionResult(lines, materialCost, expiredQtys);
    }

    static boolean isWeightLine(ProductBomLine bom, RawMaterial raw) {
        String unit = bom.getUnit() != null ? bom.getUnit().getCode() : raw.getUnit();
        return unit != null && WEIGHT_UNITS.contains(unit.trim().toLowerCase(Locale.ROOT));
    }

    /** Chi phí tạm tính: WAC liên hoàn NVL × SL cần; chưa có WAC → cộng theo giá lô FEFO. */
    private BigDecimal estimateMaterialCost(Long rawMaterialId, BigDecimal requiredQty, LocalDate today) {
        var wac = costingService.currentRawAvgUnitCost(rawMaterialId);
        if (wac.isPresent()) {
            return requiredQty.multiply(wac.get()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        List<RawMaterialLot> lots = rawMaterialLotRepository.findFefoUsable(rawMaterialId, today);
        BigDecimal remaining = requiredQty;
        BigDecimal cost = BigDecimal.ZERO;
        for (RawMaterialLot lot : lots) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = lot.getQtyRemaining().min(remaining);
            if (lot.getUnitPrice() != null) {
                cost = cost.add(lot.getUnitPrice().multiply(take));
            }
            remaining = remaining.subtract(take);
        }
        return cost.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private enum LineKind {
        WEIGHT,
        COUNT
    }

    public record ExplosionResult(
            List<ProductionOrderMaterialLine> lines,
            BigDecimal estimatedMaterialCost,
            List<BigDecimal> expiredQtys) {
        public ExplosionResult(List<ProductionOrderMaterialLine> lines, BigDecimal estimatedMaterialCost) {
            this(lines, estimatedMaterialCost, List.of());
        }
    }
}
