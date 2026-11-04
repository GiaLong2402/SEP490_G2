package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.Category;
import com.hbsisms.feature.catalog.Product;
import java.time.Instant;
import java.util.List;

/** Matches FE {@code ProductListItem} / {@code ProductDetail}. */
public record ProductResponse(
        Long id,
        String code,
        String name,
        String slug,
        Long categoryId,
        String description,
        String storageNote,
        String origin,
        String imageUrl,
        List<String> gallery,
        boolean isPublished,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt,
        String categoryName,
        List<VariantWithTiersResponse> variants) {

    public static ProductResponse from(Product product, List<VariantWithTiersResponse> variants) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getSlug(),
                category == null ? null : category.getId(),
                product.getDescription(),
                product.getStorageNote(),
                product.getOrigin(),
                product.getImageUrl(),
                product.getGallery(),
                product.isPublished(),
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                category == null ? null : category.getName(),
                variants);
    }
}
