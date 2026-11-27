package com.hbsisms.feature.catalog;

import com.hbsisms.feature.catalog.dto.ApplySellPriceRequest;
import com.hbsisms.feature.catalog.dto.SellPriceHistoryResponse;
import com.hbsisms.feature.catalog.dto.SellPriceSuggestionResponse;
import com.hbsisms.feature.catalog.dto.VariantOptionResponse;
import com.hbsisms.feature.catalog.dto.VariantRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Variant dropdown (read) + create/update with volume tiers (write — Doanh).
 * Task 59 — gợi ý / áp dụng giá bán.
 */
@RestController
public class VariantController {

    private final VariantService variantService;
    private final SellPriceSuggestionService sellPriceSuggestionService;

    public VariantController(
            VariantService variantService, SellPriceSuggestionService sellPriceSuggestionService) {
        this.variantService = variantService;
        this.sellPriceSuggestionService = sellPriceSuggestionService;
    }

    @GetMapping("/variants")
    @PreAuthorize("hasAnyAuthority('products', 'customers', 'orders', 'inventory')")
    public List<VariantOptionResponse> options() {
        return variantService.listOptions();
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public void create(@PathVariable Long productId, @Valid @RequestBody VariantRequest request) {
        variantService.create(productId, request);
    }

    @PutMapping("/variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public void update(@PathVariable Long id, @Valid @RequestBody VariantRequest request) {
        variantService.update(id, request);
    }

    /** Task 59 — xem giá gợi ý (không đổi listPrice). */
    @GetMapping("/variants/{id}/sell-price-suggestion")
    @PreAuthorize("hasAuthority('products')")
    public SellPriceSuggestionResponse suggest(
            @PathVariable Long id,
            @RequestParam(defaultValue = "WAC") SellPriceCostSource costSource) {
        return sellPriceSuggestionService.suggest(id, costSource);
    }

    /** Task 59 — Chủ/Admin bấm Áp dụng mới cập nhật giá niêm yết. */
    @PostMapping("/variants/{id}/sell-price-suggestion/apply")
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public SellPriceSuggestionResponse apply(
            @PathVariable Long id,
            @Valid @RequestBody ApplySellPriceRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return sellPriceSuggestionService.apply(id, request, actor == null ? null : actor.id());
    }

    /** Task 59 — lịch sử giá bán một quy cách. */
    @GetMapping("/variants/{id}/sell-price-history")
    @PreAuthorize("hasAuthority('products')")
    public List<SellPriceHistoryResponse> history(@PathVariable Long id) {
        return sellPriceSuggestionService.historyForVariant(id);
    }
}
