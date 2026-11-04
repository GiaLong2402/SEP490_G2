package com.hbsisms.feature.catalog.dto;

import com.hbsisms.feature.catalog.Category;

/** Matches FE {@code CategoryListItem}. */
public record CategoryResponse(
        Long id,
        String code,
        String name,
        String description,
        String imageUrl,
        Long parentId,
        int sortOrder,
        boolean isActive,
        String parentName,
        long productCount) {

    public static CategoryResponse from(Category category, long productCount) {
        Category parent = category.getParent();
        return new CategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDescription(),
                category.getImageUrl(),
                parent == null ? null : parent.getId(),
                category.getSortOrder(),
                category.isActive(),
                parent == null ? null : parent.getName(),
                productCount);
    }
}
