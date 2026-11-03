package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Tên đăng nhập là bắt buộc") @Size(max = 160) String username,
        @NotBlank(message = "Mật khẩu là bắt buộc") @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=***]";
    }
}
