package com.hbsisms.feature.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /products — matches FE {@code ProductInput}. */
public record ProductRequest(
        @NotBlank(message = "Vui lòng nhập mã sản phẩm")
        @Size(max = 50, message = "Mã sản phẩm tối đa 50 ký tự")
        String code,

        @NotBlank(message = "Vui lòng nhập tên sản phẩm")
        @Size(max = 200, message = "Tên sản phẩm tối đa 200 ký tự")
        String name,

        Long categoryId,

        String description,

        String storageNote,

        @Size(max = 160, message = "Xuất xứ tối đa 160 ký tự")
        String origin,

        @NotNull(message = "Trạng thái hiển thị cổng B2C là bắt buộc")
        Boolean isPublished,

        @NotNull(message = "Trạng thái kinh doanh là bắt buộc")
        Boolean isActive) {
}
