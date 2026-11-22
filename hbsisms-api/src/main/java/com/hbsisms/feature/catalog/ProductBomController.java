package com.hbsisms.feature.catalog;

import com.hbsisms.feature.catalog.dto.BomReplaceRequest;
import com.hbsisms.feature.catalog.dto.BomResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products/{productId}/bom")
public class ProductBomController {

    private final ProductBomService productBomService;

    public ProductBomController(ProductBomService productBomService) {
        this.productBomService = productBomService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('products')")
    public BomResponse get(@PathVariable Long productId) {
        return productBomService.get(productId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('products')")
    public BomResponse replace(@PathVariable Long productId, @Valid @RequestBody BomReplaceRequest request) {
        return productBomService.replace(productId, request);
    }
}
