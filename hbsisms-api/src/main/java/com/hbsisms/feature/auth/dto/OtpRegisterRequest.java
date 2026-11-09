package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inter 2 task 2 — customer self-registration after Firebase (or stub) phone OTP. */
public record OtpRegisterRequest(
        @NotBlank(message = "idToken là bắt buộc") @Size(max = 4096) String idToken,
        @NotBlank(message = "Họ tên là bắt buộc") @Size(max = 160) String fullName,
        @Email(message = "Email không hợp lệ") @Size(max = 160) String email) {

    @Override
    public String toString() {
        return "OtpRegisterRequest[idToken=***, fullName=" + fullName + ", email=" + email + "]";
    }
}
