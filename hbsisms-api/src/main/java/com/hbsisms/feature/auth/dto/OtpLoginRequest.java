package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Task 1 Inter 2 — login with Firebase (or stub) ID token after phone OTP. */
public record OtpLoginRequest(
        @NotBlank(message = "idToken là bắt buộc") @Size(max = 4096) String idToken) {

    @Override
    public String toString() {
        return "OtpLoginRequest[idToken=***]";
    }
}
