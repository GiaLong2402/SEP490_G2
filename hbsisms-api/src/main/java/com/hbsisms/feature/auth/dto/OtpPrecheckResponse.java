package com.hbsisms.feature.auth.dto;

/** Kết quả cho phép gửi OTP (sau khi đã ghi nhận 1 lần trong cửa sổ). */
public record OtpPrecheckResponse(boolean allowed, int remaining, int windowSeconds) {}
