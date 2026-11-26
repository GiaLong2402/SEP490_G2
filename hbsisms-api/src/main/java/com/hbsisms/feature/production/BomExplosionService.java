package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc BOM × SL kế hoạch → snapshot NVL + đối chiếu tồn dùng được + chi phí tạm tính. */
@Service
@Transactional(readOnly = true)
public class BomExplosionService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int QTY_SCALE = 4;
    private static final int MONEY_SCALE = 2;

    private final ProductBomLineRepository bomLineRepository;
    private final RawMaterialLotRepository rawMaterialLotRepository;
    private final Clock clock;

    public BomExplosionService(
            ProductBomLineRepository bomLineRepository,
            RawMaterialLotRepository rawMaterialLotRepository,
            Clock clock) {
        this.bomLineRepository = bomLineRepository;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
        this.clock = clock;
    }

    /**
     * {@code noRollbackFor}: caller có thể catch khi thiếu BOM (vd. tạo lệnh từ đơn cọc)
     * mà không làm TX ngoài thành rollback-only.
     */
    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ExplosionResult explode(Long productId, BigDecimal plannedQty) {
        if (plannedQty == null || plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Số lượng kế hoạch phải > 0");
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

            BigDecimal waste = bom.getWastePercent() == null ? BigDecimal.ZERO : bom.getWastePercent();
            BigDecimal factor = BigDecimal.ONE.add(waste.divide(HUNDRED, 8, RoundingMode.HALF_UP));
            BigDecimal required = bom.getQty()
                    .multiply(plannedQty)
                    .multiply(factor)
                    .setScale(QTY_SCALE, RoundingMode.HALF_UP);

            // Inter1 #48 note: tồn dùng được không tính lô NVL đã hết hạn.
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
            line.setBomQtyPerUnit(bom.getQty());
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

    /** Chi phí tạm tính theo giá lô FEFO còn dùng được; không có giá → 0. */
    private BigDecimal estimateMaterialCost(Long rawMaterialId, BigDecimal requiredQty, LocalDate today) {
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

    public record ExplosionResult(
            List<ProductionOrderMaterialLine> lines,
            BigDecimal estimatedMaterialCost,
            List<BigDecimal> expiredQtys) {
        public ExplosionResult(List<ProductionOrderMaterialLine> lines, BigDecimal estimatedMaterialCost) {
            this(lines, estimatedMaterialCost, List.of());
        }
    }
}
