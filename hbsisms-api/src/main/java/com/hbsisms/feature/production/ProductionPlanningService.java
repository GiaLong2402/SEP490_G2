package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.PackSizes;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.production.dto.ProductionBomProductResponse;
import com.hbsisms.feature.production.dto.ProductionMaterialNeedResponse;
import com.hbsisms.feature.production.dto.ProductionMaterialPreviewResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API phụ trợ lập lệnh sản xuất cho quyền {@code inventory} (Inter1 #48 note):
 * danh sách SP đã có BOM + xem trước NVL cần dùng / tồn dùng được.
 */
@Service
@Transactional(readOnly = true)
public class ProductionPlanningService {

    private final ProductBomLineRepository bomLineRepository;
    private final ProductRepository productRepository;
    private final BomExplosionService bomExplosionService;
    private final ProductVariantRepository productVariantRepository;

    public ProductionPlanningService(
            ProductBomLineRepository bomLineRepository,
            ProductRepository productRepository,
            BomExplosionService bomExplosionService,
            ProductVariantRepository productVariantRepository) {
        this.bomLineRepository = bomLineRepository;
        this.productRepository = productRepository;
        this.bomExplosionService = bomExplosionService;
        this.productVariantRepository = productVariantRepository;
    }

    public List<ProductionBomProductResponse> listBomProducts() {
        return bomLineRepository.findActiveProductsWithRawBom().stream()
                .map(p -> ProductionBomProductResponse.from(
                        p,
                        bomLineRepository.countRawLinesByProductId(p.getId()),
                        activeVariants(p.getId())))
                .toList();
    }

    /** Loại khay chọn được khi lập lệnh — khay 1 kg lên trước. */
    private List<ProductVariant> activeVariants(Long productId) {
        List<ProductVariant> active = productVariantRepository.findByProduct_IdOrderByIdAsc(productId).stream()
                .filter(ProductVariant::isActive)
                .toList();
        List<ProductVariant> sorted = new ArrayList<>(active.stream().filter(PackSizes::isOneKg).toList());
        active.stream().filter(v -> !PackSizes.isOneKg(v)).forEach(sorted::add);
        return sorted;
    }

    public ProductionMaterialPreviewResponse previewMaterials(Long productId, BigDecimal plannedQty) {
        return previewMaterials(productId, plannedQty, null);
    }

    public ProductionMaterialPreviewResponse previewMaterials(Long productId, BigDecimal plannedQty, Long variantId) {
        if (productId == null) {
            throw ApiException.badRequest("Thiếu productId");
        }
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        if (!product.isActive()) {
            throw ApiException.badRequest("Sản phẩm đã ngừng kinh doanh");
        }
        if (product.isRawMaterial()) {
            throw ApiException.badRequest("Chỉ xem trước được thành phẩm, không phải nguyên liệu");
        }

        // variantId bỏ qua — kế hoạch theo khay quy đổi 1 kg; chỉ NVL theo kg (VT đóng gói lúc pack).
        BomExplosionService.ExplosionResult explosion = bomExplosionService.explode(productId, plannedQty);
        List<ProductionMaterialNeedResponse> materials = new ArrayList<>();
        List<ProductionOrderMaterialLine> lines = explosion.lines();
        List<BigDecimal> expired = explosion.expiredQtys();
        boolean allOk = true;
        for (int i = 0; i < lines.size(); i++) {
            ProductionOrderMaterialLine line = lines.get(i);
            BigDecimal expiredQty = i < expired.size() ? expired.get(i) : BigDecimal.ZERO;
            materials.add(ProductionMaterialNeedResponse.from(line, expiredQty));
            if (!line.isSufficient()) {
                allOk = false;
            }
        }
        return new ProductionMaterialPreviewResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                plannedQty,
                explosion.estimatedMaterialCost(),
                allOk,
                materials);
    }

    /** VT đóng gói (xốp, túi…) theo số khay vật lý lúc hoàn thành. */
    public ProductionMaterialPreviewResponse previewPackaging(Long productId, BigDecimal physicalTrayCount) {
        if (productId == null) {
            throw ApiException.badRequest("Thiếu productId");
        }
        if (physicalTrayCount == null || physicalTrayCount.signum() <= 0) {
            throw ApiException.badRequest("Số khay vật lý phải > 0");
        }
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        if (!product.isActive()) {
            throw ApiException.badRequest("Sản phẩm đã ngừng kinh doanh");
        }

        BomExplosionService.ExplosionResult explosion =
                bomExplosionService.explodePackaging(productId, physicalTrayCount);
        List<ProductionMaterialNeedResponse> materials = new ArrayList<>();
        List<ProductionOrderMaterialLine> lines = explosion.lines();
        List<BigDecimal> expired = explosion.expiredQtys();
        boolean allOk = true;
        for (int i = 0; i < lines.size(); i++) {
            ProductionOrderMaterialLine line = lines.get(i);
            BigDecimal expiredQty = i < expired.size() ? expired.get(i) : BigDecimal.ZERO;
            materials.add(ProductionMaterialNeedResponse.from(line, expiredQty));
            if (!line.isSufficient()) {
                allOk = false;
            }
        }
        return new ProductionMaterialPreviewResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                physicalTrayCount,
                explosion.estimatedMaterialCost(),
                allOk,
                materials);
    }
}
