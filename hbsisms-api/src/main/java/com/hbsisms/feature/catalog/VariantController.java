package com.hbsisms.feature.catalog;

import com.hbsisms.feature.catalog.dto.VariantOptionResponse;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only variant list for FE dropdowns. Full product/variant CRUD belongs to Cường/Doanh.
 */
@RestController
public class VariantController {

    private final ProductVariantRepository productVariantRepository;

    public VariantController(ProductVariantRepository productVariantRepository) {
        this.productVariantRepository = productVariantRepository;
    }

    @GetMapping("/variants")
    @PreAuthorize("hasAnyAuthority('products', 'customers', 'orders', 'inventory')")
    public List<VariantOptionResponse> options() {
        return productVariantRepository.findAll(Sort.by("id")).stream()
                .filter(ProductVariant::isActive)
                .map(VariantOptionResponse::from)
                .toList();
    }
}
