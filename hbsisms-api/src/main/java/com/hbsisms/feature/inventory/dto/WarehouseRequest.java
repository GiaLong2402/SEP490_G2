package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT /warehouses — matches FE {@code WarehouseInput}. */
public record WarehouseRequest(
        @NotBlank(message = "Vui lòng nhập mã và tên kho")
        @Size(max = 30, message = "Mã kho tối đa 30 ký tự")
        String code,

        @NotBlank(message = "Vui lòng nhập mã và tên kho")
        @Size(max = 160, message = "Tên kho tối đa 160 ký tự")
        String name,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @NotNull(message = "Trạng thái là bắt buộc")
        Boolean isActive) {
}
