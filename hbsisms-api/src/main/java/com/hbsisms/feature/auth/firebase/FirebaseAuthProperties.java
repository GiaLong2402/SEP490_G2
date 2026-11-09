package com.hbsisms.feature.auth.firebase;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.firebase")
public record FirebaseAuthProperties(
        /** {@code stub} for local/dev/tests; {@code firebase} reserved for Admin SDK (not wired yet). */
        @NotBlank String mode) {
}
