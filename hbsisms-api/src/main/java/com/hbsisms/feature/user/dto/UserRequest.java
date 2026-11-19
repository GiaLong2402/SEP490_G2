package com.hbsisms.feature.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of POST/PUT /admin/users.
 * {@code password}: required on create; on update optional — non-blank = đặt lại mật khẩu.
 */
public record UserRequest(
        @NotBlank(message = "Tên đăng nhập là bắt buộc")
        @Pattern(regexp = "^\\s*[A-Za-z0-9._-]{3,60}\\s*$",
                message = "Tên đăng nhập 3-60 ký tự, chỉ gồm chữ, số, dấu chấm, gạch dưới, gạch ngang")
        String username,

        @NotBlank(message = "Họ tên là bắt buộc")
        @Size(max = 160, message = "Họ tên tối đa 160 ký tự")
        String fullName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 160, message = "Email tối đa 160 ký tự")
        String email,

        /** Required for staff OTP login (Inter 2 task 3). Accepts 0xxxxxxxxx or +84…; service normalizes. */
        @NotBlank(message = "Số điện thoại là bắt buộc")
        @Pattern(regexp = "^\\s*[0-9+ ().-]{9,20}\\s*$", message = "Số điện thoại không hợp lệ")
        String phone,

        @NotNull(message = "Vai trò là bắt buộc")
        Short roleId,

        @Size(max = 80, message = "Chức vụ tối đa 80 ký tự")
        String position,

        @Size(max = 30, message = "Mã nhân viên tối đa 30 ký tự")
        String employeeCode,

        @NotNull(message = "Trạng thái là bắt buộc")
        Boolean isActive,

        @Size(min = 8, max = 72, message = "Mật khẩu 8-72 ký tự")
        String password) {
}
