package com.hbsisms.feature.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Body precheck trước khi client gửi OTP Firebase. */
public record OtpPrecheckRequest(@NotBlank(message = "Thiếu số điện thoại") String phone) {}
