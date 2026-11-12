package com.hbsisms.feature.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Địa chỉ giao nhập nhanh khi tạo đơn khách vãng lai (BE sẽ tạo {@code CustomerAddress}). */
public record ShipAddressInput(
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
        BigDecimal distanceKm) {
}
