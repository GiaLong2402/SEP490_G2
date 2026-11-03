package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.customer.AddressKind;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of POST/PUT /customers/{id}/addresses. Matches FE {@code AddressInput}. */
public record AddressRequest(
        AddressKind kind,

        @Size(max = 160, message = "Tên người nhận tối đa 160 ký tự")
        String recipientName,

        @Pattern(regexp = "^$|^[0-9+ ().-]{6,20}$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotBlank(message = "Địa chỉ là bắt buộc")
        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String line1,

        @Size(max = 120, message = "Phường/xã tối đa 120 ký tự")
        String ward,

        @Size(max = 120, message = "Quận/huyện tối đa 120 ký tự")
        String district,

        @Size(max = 120, message = "Tỉnh/thành tối đa 120 ký tự")
        String province,

        @DecimalMin(value = "0", message = "Khoảng cách không được âm")
        BigDecimal distanceKm,

        @NotNull(message = "isDefault là bắt buộc")
        Boolean isDefault) {
}
