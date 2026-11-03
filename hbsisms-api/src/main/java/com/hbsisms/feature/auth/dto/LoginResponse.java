package com.hbsisms.feature.auth.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, CurrentUserResponse user) {
}
