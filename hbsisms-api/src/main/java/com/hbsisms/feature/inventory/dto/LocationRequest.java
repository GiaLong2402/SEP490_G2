package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST/PUT location — matches FE {@code LocationInput}. */
public record LocationRequest(
        @NotBlank(message = "Vui lòng nhập mã vị trí")
        @Size(max = 30, message = "Mã vị trí tối đa 30 ký tự")
        String code,

        @Size(max = 60, message = "Khu vực tối đa 60 ký tự")
        String zone,

        @NotNull(message = "Vui lòng nhập sức chứa vị trí")
        Integer capacity,

        @Size(max = 200, message = "Ghi chú tối đa 200 ký tự")
        String note) {
}
