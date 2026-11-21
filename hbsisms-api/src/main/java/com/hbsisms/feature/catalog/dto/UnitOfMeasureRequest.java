package com.hbsisms.feature.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /units. */
public record UnitOfMeasureRequest(
        @NotBlank(message = "Vui lòng nhập mã đơn vị")
                @Size(max = 20, message = "Mã đơn vị tối đa 20 ký tự")
                String code,
        @NotBlank(message = "Vui lòng nhập tên đơn vị")
                @Size(max = 80, message = "Tên đơn vị tối đa 80 ký tự")
                String name,
        @NotNull(message = "Trạng thái là bắt buộc") Boolean isActive) {
}
