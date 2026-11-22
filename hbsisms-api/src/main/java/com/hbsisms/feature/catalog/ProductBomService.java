package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.BomLineRequest;
import com.hbsisms.feature.catalog.dto.BomLineResponse;
import com.hbsisms.feature.catalog.dto.BomReplaceRequest;
import com.hbsisms.feature.catalog.dto.BomResponse;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductBomService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductRepository productRepository;
    private final ProductBomLineRepository bomLineRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final UnitOfMeasureRepository unitRepository;

    public ProductBomService(
            ProductRepository productRepository,
            ProductBomLineRepository bomLineRepository,
            RawMaterialRepository rawMaterialRepository,
            UnitOfMeasureRepository unitRepository) {
        this.productRepository = productRepository;
        this.bomLineRepository = bomLineRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.unitRepository = unitRepository;
    }

    public BomResponse get(Long productId) {
        Product product = requireProduct(productId);
        List<BomLineResponse> lines = bomLineRepository
                .findByProduct_IdOrderBySortOrderAscIdAsc(productId)
                .stream()
                .map(BomLineResponse::from)
                .toList();
        return new BomResponse(product.getId(), product.getCode(), product.getName(), lines);
    }

    @Transactional
    public BomResponse replace(Long productId, BomReplaceRequest request) {
        Product product = requireProduct(productId);
        List<BomLineRequest> lines = request.lines() == null ? List.of() : request.lines();

        Set<Long> rawIds = new HashSet<>();
        Set<Long> componentIds = new HashSet<>();
        List<ProductBomLine> entities = new java.util.ArrayList<>();
        int order = 0;
        for (BomLineRequest line : lines) {
            entities.add(buildLine(product, line, order++, rawIds, componentIds));
        }

        bomLineRepository.deleteByProduct_Id(productId);
        bomLineRepository.flush();
        bomLineRepository.saveAll(entities);
        return get(productId);
    }

    private ProductBomLine buildLine(
            Product product,
            BomLineRequest request,
            int sortOrder,
            Set<Long> rawIds,
            Set<Long> componentIds) {
        boolean hasRaw = request.rawMaterialId() != null;
        boolean hasComponent = request.componentProductId() != null;
        if (hasRaw == hasComponent) {
            throw ApiException.badRequest("Mỗi dòng chọn đúng một: nguyên liệu hoặc sản phẩm cấu thành");
        }

        ProductBomLine line = new ProductBomLine();
        line.setProduct(product);
        line.setQty(request.qty());
        line.setWastePercent(normalizeWaste(request.wastePercent()));
        line.setUnit(resolveUnit(request.unitId()));
        line.setSortOrder(sortOrder);

        if (hasRaw) {
            if (!rawIds.add(request.rawMaterialId())) {
                throw ApiException.badRequest("Nguyên liệu bị trùng trong định mức");
            }
            RawMaterial raw = rawMaterialRepository
                    .findById(request.rawMaterialId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
            if (!raw.isActive()) {
                throw ApiException.badRequest("Nguyên liệu đã ngừng sử dụng");
            }
            if (raw.getCode().equalsIgnoreCase(product.getCode())) {
                throw ApiException.badRequest("Không thể chọn chính sản phẩm này làm nguyên liệu của nó");
            }
            line.setRawMaterial(raw);
            line.setComponentProduct(null);
        } else {
            Long componentId = request.componentProductId();
            if (componentId.equals(product.getId())) {
                throw ApiException.badRequest("Không thể chọn chính sản phẩm này làm nguyên liệu của nó");
            }
            if (!componentIds.add(componentId)) {
                throw ApiException.badRequest("Sản phẩm cấu thành bị trùng trong định mức");
            }
            Product component = productRepository
                    .findById(componentId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm cấu thành"));
            if (!component.isActive()) {
                throw ApiException.badRequest("Sản phẩm cấu thành đã ngừng kinh doanh");
            }
            line.setComponentProduct(component);
            line.setRawMaterial(null);
        }
        return line;
    }

    private UnitOfMeasure resolveUnit(Long unitId) {
        UnitOfMeasure unit = unitRepository
                .findById(unitId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn vị tính"));
        if (!unit.isActive()) {
            throw ApiException.badRequest("Đơn vị tính đã ngừng sử dụng");
        }
        return unit;
    }

    private static BigDecimal normalizeWaste(BigDecimal wastePercent) {
        if (wastePercent == null) {
            return null;
        }
        if (wastePercent.compareTo(BigDecimal.ZERO) < 0 || wastePercent.compareTo(HUNDRED) > 0) {
            throw ApiException.badRequest("% hao hụt phải từ 0 đến 100");
        }
        return wastePercent;
    }

    private Product requireProduct(Long productId) {
        return productRepository
                .findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
    }
}
