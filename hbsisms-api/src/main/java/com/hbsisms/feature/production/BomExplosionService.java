package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductBomLine;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc BOM × SL kế hoạch → snapshot NVL + đối chiếu tồn + chi phí tạm tính. */
@Service
@Transactional(readOnly = true)
public class BomExplosionService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int QTY_SCALE = 4;
    private static final int MONEY_SCALE = 2;

    private final ProductBomLineRepository bomLineRepository;
    private final RawMaterialLotRepository rawMaterialLotRepository;

    public BomExplosionService(
            ProductBomLineRepository bomLineRepository, RawMaterialLotRepository rawMaterialLotRepository) {
        this.bomLineRepository = bomLineRepository;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
    }

    public ExplosionResult explode(Long productId, BigDecimal plannedQty) {
        if (plannedQty == null || plannedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Số lượng kế hoạch phải > 0");
        }
        List<ProductBomLine> bomLines = bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(productId);
        if (bomLines.isEmpty()) {
            throw ApiException.badRequest("Sản phẩm chưa có định mức nguyên liệu (BOM)");
        }

        List<ProductionOrderMaterialLine> lines = new ArrayList<>();
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

            BigDecimal onHand = raw.getQtyOnHand() == null ? BigDecimal.ZERO : raw.getQtyOnHand();
            boolean sufficient = onHand.compareTo(required) >= 0;
            BigDecimal lineCost = estimateMaterialCost(raw.getId(), required);

            ProductionOrderMaterialLine line = new ProductionOrderMaterialLine();
            line.setRawMaterial(raw);
            line.setBomQtyPerUnit(bom.getQty());
            line.setWastePercent(bom.getWastePercent());
            line.setRequiredQty(required);
            line.setOnHandQty(onHand);
            line.setSufficient(sufficient);
            line.setLineEstimatedCost(lineCost);
            lines.add(line);
            materialCost = materialCost.add(lineCost);
        }

        return new ExplosionResult(lines, materialCost);
    }

    /** Chi phí tạm tính theo giá lô FEFO còn tồn; không có giá → 0. */
    private BigDecimal estimateMaterialCost(Long rawMaterialId, BigDecimal requiredQty) {
        List<RawMaterialLot> lots = rawMaterialLotRepository.findFefo(rawMaterialId, true);
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

    public record ExplosionResult(List<ProductionOrderMaterialLine> lines, BigDecimal estimatedMaterialCost) {}
}
