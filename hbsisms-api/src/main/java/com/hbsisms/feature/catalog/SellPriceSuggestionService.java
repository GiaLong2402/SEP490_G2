package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.ApplySellPriceRequest;
import com.hbsisms.feature.catalog.dto.SellPriceHistoryResponse;
import com.hbsisms.feature.catalog.dto.SellPriceSuggestionResponse;
import com.hbsisms.feature.costing.CostingService;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 59 — gợi ý giá bán từ WAC; Áp dụng đổi listPrice + ghi bảng lịch sử giai đoạn + audit.
 */
@Service
@Transactional(readOnly = true)
public class SellPriceSuggestionService {

    public static final String REF_TYPE = "product_variant";

    private final ProductVariantRepository productVariantRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final SellPriceHistoryRepository sellPriceHistoryRepository;
    private final CostingService costingService;
    private final AppConfigService appConfigService;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    public SellPriceSuggestionService(
            ProductVariantRepository productVariantRepository,
            ProductionOrderRepository productionOrderRepository,
            SellPriceHistoryRepository sellPriceHistoryRepository,
            CostingService costingService,
            AppConfigService appConfigService,
            AuditLogService auditLogService,
            UserRepository userRepository) {
        this.productVariantRepository = productVariantRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.sellPriceHistoryRepository = sellPriceHistoryRepository;
        this.costingService = costingService;
        this.appConfigService = appConfigService;
        this.auditLogService = auditLogService;
        this.userRepository = userRepository;
    }

    public SellPriceSuggestionResponse suggest(Long variantId, SellPriceCostSource source) {
        ProductVariant variant = requireVariant(variantId);
        SellPriceCostSource costSource = source == null ? SellPriceCostSource.WAC : source;
        CostSnapshot cost = resolveCost(variant, costSource);
        Markup markup = resolveMarkup(variant.getProduct());
        BigDecimal suggested = appConfigService.sellPriceFromUnitCost(cost.unitCost(), markup.percent());
        if (suggested == null) {
            throw ApiException.badRequest(
                    "Không tính được giá gợi ý — thiếu giá vốn hợp lệ cho nguồn " + costSource);
        }
        return new SellPriceSuggestionResponse(
                variant.getId(),
                variant.getSku(),
                variant.getPackLabel(),
                variant.getListPrice(),
                costSource,
                cost.unitCost(),
                cost.label(),
                cost.productionOrderId(),
                cost.productionOrderCode(),
                markup.percent(),
                markup.source(),
                suggested);
    }

    public List<SellPriceHistoryResponse> historyForVariant(Long variantId) {
        requireVariant(variantId);
        return withActorNames(sellPriceHistoryRepository.findByVariant_IdOrderByEffectiveFromDesc(variantId));
    }

    public List<SellPriceHistoryResponse> historyForProduct(Long productId) {
        return withActorNames(sellPriceHistoryRepository.findByProductIdOrderByEffectiveFromDesc(productId));
    }

    @Transactional
    public SellPriceSuggestionResponse apply(Long variantId, ApplySellPriceRequest request, Long actorId) {
        ProductVariant requested = requireVariant(variantId);
        // Luôn neo giá theo khay 1 kg (WAC + % lời); khay 0,5 kg = nửa giá 1 kg.
        ProductVariant oneKg = PackSizes.isOneKg(requested)
                ? requested
                : findSibling(requested, true).orElse(requested);
        SellPriceSuggestionResponse suggestion = suggest(oneKg.getId(), request.costSource());
        BigDecimal newOneKgPrice = suggestion.suggestedListPrice();
        Instant now = Instant.now();

        writeListPrice(oneKg, newOneKgPrice, suggestion, actorId, now);

        ProductVariant half = PackSizes.isHalfKg(requested)
                ? requested
                : findSibling(oneKg, false).orElse(null);
        BigDecimal halfPrice = appConfigService.halfKgSellPrice(newOneKgPrice);
        if (half != null && halfPrice != null && !half.getId().equals(oneKg.getId())) {
            writeListPrice(
                    half,
                    halfPrice,
                    new SellPriceSuggestionResponse(
                            half.getId(),
                            half.getSku(),
                            half.getPackLabel(),
                            half.getListPrice(),
                            suggestion.costSource(),
                            suggestion.unitCost() == null
                                    ? null
                                    : suggestion
                                            .unitCost()
                                            .multiply(new BigDecimal("0.5"))
                                            .setScale(2, RoundingMode.HALF_UP),
                            suggestion.costSourceLabel() + " · quy đổi 0,5 kg",
                            suggestion.productionOrderId(),
                            suggestion.productionOrderCode(),
                            suggestion.markupPercent(),
                            suggestion.markupSource(),
                            halfPrice),
                    actorId,
                    now);
        }

        return new SellPriceSuggestionResponse(
                suggestion.variantId(),
                suggestion.sku(),
                suggestion.packLabel(),
                newOneKgPrice,
                suggestion.costSource(),
                suggestion.unitCost(),
                suggestion.costSourceLabel(),
                suggestion.productionOrderId(),
                suggestion.productionOrderCode(),
                suggestion.markupPercent(),
                suggestion.markupSource(),
                newOneKgPrice);
    }

    private void writeListPrice(
            ProductVariant variant,
            BigDecimal newPrice,
            SellPriceSuggestionResponse suggestion,
            Long actorId,
            Instant now) {
        BigDecimal oldPrice = variant.getListPrice();
        sellPriceHistoryRepository
                .findFirstByVariant_IdAndEffectiveToIsNullOrderByEffectiveFromDesc(variant.getId())
                .ifPresent(open -> {
                    open.setEffectiveTo(now);
                    sellPriceHistoryRepository.save(open);
                });

        SellPriceHistory row = new SellPriceHistory();
        row.setVariant(variant);
        row.setListPrice(newPrice);
        row.setPreviousListPrice(oldPrice);
        row.setUnitCost(suggestion.unitCost());
        row.setCostSource(suggestion.costSource());
        row.setMarkupPercent(suggestion.markupPercent());
        row.setMarkupSource(suggestion.markupSource());
        row.setEffectiveFrom(now);
        row.setEffectiveTo(null);
        row.setCreatedByUserId(actorId);
        sellPriceHistoryRepository.save(row);

        variant.setListPrice(newPrice);
        productVariantRepository.save(variant);

        Map<String, Object> auditDetail = new HashMap<>();
        auditDetail.put("sku", variant.getSku());
        auditDetail.put("productId", variant.getProduct().getId());
        auditDetail.put("oldListPrice", oldPrice == null ? BigDecimal.ZERO : oldPrice);
        auditDetail.put("newListPrice", newPrice);
        auditDetail.put("unitCost", suggestion.unitCost());
        auditDetail.put("costSource", suggestion.costSource().name());
        auditDetail.put("markupPercent", suggestion.markupPercent());
        auditDetail.put("markupSource", suggestion.markupSource());
        auditDetail.put("historyId", row.getId());
        auditLogService.record(actorId, "apply_sell_price", REF_TYPE, variant.getId(), auditDetail);
    }

    private Optional<ProductVariant> findSibling(ProductVariant variant, boolean wantOneKg) {
        Long productId = variant.getProduct().getId();
        return productVariantRepository.findByProduct_IdOrderByIdAsc(productId).stream()
                .filter(ProductVariant::isActive)
                .filter(v -> !v.getId().equals(variant.getId()))
                .filter(v -> wantOneKg ? PackSizes.isOneKg(v) : PackSizes.isHalfKg(v))
                .findFirst();
    }

    private List<SellPriceHistoryResponse> withActorNames(List<SellPriceHistory> rows) {
        Set<Long> userIds = new HashSet<>();
        for (SellPriceHistory row : rows) {
            if (row.getCreatedByUserId() != null) {
                userIds.add(row.getCreatedByUserId());
            }
        }
        Map<Long, String> names = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (AppUser user : userRepository.findAllById(userIds)) {
                names.put(user.getId(), user.getFullName());
            }
        }
        return rows.stream()
                .map(row -> SellPriceHistoryResponse.from(
                        row, row.getCreatedByUserId() == null ? null : names.get(row.getCreatedByUserId())))
                .toList();
    }

    private CostSnapshot resolveCost(ProductVariant variant, SellPriceCostSource source) {
        if (source == SellPriceCostSource.PRODUCTION) {
            return productionCost(variant.getId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "Chưa có lệnh SX hoàn thành cho quy cách này — chọn nguồn WAC hoặc nhập TP trước"));
        }
        return costingService
                .currentFinishedAvgUnitCost(variant.getId())
                .map(c -> new CostSnapshot(c, "ĐG bình quân kỳ WAC hiện hành", null, null))
                .or(() -> productionCost(variant.getId()))
                .orElseThrow(() -> ApiException.badRequest(
                        "Chưa có ĐG WAC và chưa có lệnh SX hoàn thành cho quy cách này"));
    }

    private Optional<CostSnapshot> productionCost(Long variantId) {
        return productionOrderRepository
                .findFirstByFinishedVariant_IdAndStatusOrderByIdDesc(variantId, ProductionOrderStatus.COMPLETED)
                .flatMap(this::unitCostFromOrder);
    }

    private Optional<CostSnapshot> unitCostFromOrder(ProductionOrder order) {
        BigDecimal qty = order.getActualQty();
        if (qty == null || qty.signum() <= 0) {
            return Optional.empty();
        }
        BigDecimal material = order.getActualMaterialCost() == null ? BigDecimal.ZERO : order.getActualMaterialCost();
        BigDecimal production =
                order.getActualProductionCost() == null ? BigDecimal.ZERO : order.getActualProductionCost();
        BigDecimal unit = material.add(production).divide(qty, 2, RoundingMode.HALF_UP);
        if (unit.signum() <= 0) {
            return Optional.empty();
        }
        return Optional.of(new CostSnapshot(
                unit, "Giá thành lệnh " + order.getCode(), order.getId(), order.getCode()));
    }

    private Markup resolveMarkup(Product product) {
        if (product.getSellPriceMarkupPercent() != null) {
            return new Markup(Math.max(0, product.getSellPriceMarkupPercent()), "sản phẩm");
        }
        Category category = product.getCategory();
        if (category != null && category.getSellPriceMarkupPercent() != null) {
            return new Markup(Math.max(0, category.getSellPriceMarkupPercent()), "danh mục");
        }
        return new Markup(appConfigService.sellPriceMarkupPercent(), "cấu hình chung");
    }

    private ProductVariant requireVariant(Long id) {
        return productVariantRepository
                .findWithProductAndCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách"));
    }

    private record CostSnapshot(
            BigDecimal unitCost, String label, Long productionOrderId, String productionOrderCode) {}

    private record Markup(int percent, String source) {}
}
