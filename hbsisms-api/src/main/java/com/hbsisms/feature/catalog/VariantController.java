package com.hbsisms.feature.catalog;

import com.hbsisms.feature.catalog.dto.VariantOptionResponse;
import com.hbsisms.feature.catalog.dto.VariantRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Variant dropdown (read) + create/update with volume tiers (write — Doanh).
 */
@RestController
public class VariantController {

    private final ProductVariantRepository productVariantRepository;
    private final VariantService variantService;

    public VariantController(
            ProductVariantRepository productVariantRepository, VariantService variantService) {
        this.productVariantRepository = productVariantRepository;
        this.variantService = variantService;
    }

    @GetMapping("/variants")
    @PreAuthorize("hasAnyAuthority('products', 'customers', 'orders', 'inventory')")
    public List<VariantOptionResponse> options() {
        return productVariantRepository.findAll(Sort.by("id")).stream()
                .filter(ProductVariant::isActive)
                .map(VariantOptionResponse::from)
                .toList();
    }

    @PostMapping("/products/{productId}/variants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('products')")
    public void create(@PathVariable Long productId, @Valid @RequestBody VariantRequest request) {
        variantService.create(productId, request);
    }

    @PutMapping("/variants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('products')")
    public void update(@PathVariable Long id, @Valid @RequestBody VariantRequest request) {
        variantService.update(id, request);
    }
}
