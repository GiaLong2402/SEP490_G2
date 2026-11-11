package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inter 2 #26 — PATCH /me/profile (fullName + email; phone đổi qua /me/phone). */
public record MeProfileRequest(
        @NotBlank(message = "Họ và tên là bắt buộc")
        @Size(max = 160, message = "Họ và tên tối đa 160 ký tự")
        String fullName,

        @Email(message = "Email không hợp lệ")
        @Size(max = 160, message = "Email tối đa 160 ký tự")
        String email) {
}
