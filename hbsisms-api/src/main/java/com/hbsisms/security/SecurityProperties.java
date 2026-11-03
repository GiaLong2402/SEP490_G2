package com.hbsisms.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull Cors cors, @Valid @NotNull Login login) {

    private static final int MIN_SECRET_BYTES = 32;

    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTokenTtl) {
        public Jwt {
            if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
                throw new IllegalArgumentException(
                        "app.security.jwt.secret (JWT_SECRET) must be set and at least " + MIN_SECRET_BYTES + " bytes");
            }
        }
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
        public Cors {
            allowedOrigins = List.copyOf(allowedOrigins);
        }
    }

    public record Login(@Min(1) int maxFailedAttempts, @NotNull Duration lockDuration) {
    }
}
