package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Inter 2 #26 — POST /me/phone after Firebase/stub OTP on the new number. */
public record MePhoneRequest(
        @NotBlank(message = "idToken là bắt buộc") @Size(max = 4096) String idToken) {

    @Override
    public String toString() {
        return "MePhoneRequest[idToken=***]";
    }
}
