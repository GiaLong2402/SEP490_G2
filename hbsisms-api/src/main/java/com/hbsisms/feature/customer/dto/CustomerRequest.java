package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.customer.CustomerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of POST/PUT /customers — matches FE {@code CustomerInput}. */
public record CustomerRequest(
        @NotNull(message = "Loại khách hàng là bắt buộc")
        CustomerType customerType,

        @NotBlank(message = "Vui lòng nhập tên khách")
        @Size(max = 200, message = "Tên khách tối đa 200 ký tự")
        String name,

        @Size(max = 160, message = "Email tối đa 160 ký tự")
        String email,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @Size(max = 20, message = "Mã số thuế tối đa 20 ký tự")
        String taxCode,

        @Size(max = 160, message = "Người liên hệ tối đa 160 ký tự")
        String contactName,

        @Size(max = 20, message = "SĐT liên hệ tối đa 20 ký tự")
        String contactPhone,

        BigDecimal creditLimit,

        Short netDays,

        @NotNull(message = "Trạng thái hoạt động là bắt buộc")
        Boolean isActive,

        String note) {
}
