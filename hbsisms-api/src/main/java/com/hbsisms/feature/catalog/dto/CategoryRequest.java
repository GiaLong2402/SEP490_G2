package com.hbsisms.feature.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /categories — matches FE {@code CategoryInput}. */
public record CategoryRequest(
        @NotBlank(message = "Vui lòng nhập mã loại")
        @Size(max = 50, message = "Mã loại tối đa 50 ký tự")
        String code,

        @NotBlank(message = "Vui lòng nhập tên loại")
        @Size(max = 160, message = "Tên loại tối đa 160 ký tự")
        String name,

        String description,

        Long parentId,

        @NotNull(message = "Thứ tự hiển thị là bắt buộc")
        Integer sortOrder,

        @NotNull(message = "Trạng thái là bắt buộc")
        Boolean isActive) {
}
