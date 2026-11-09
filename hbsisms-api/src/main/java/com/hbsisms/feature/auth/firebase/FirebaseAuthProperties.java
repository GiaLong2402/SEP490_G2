package com.hbsisms.feature.auth.firebase;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.firebase")
public record FirebaseAuthProperties(
        /** {@code stub} = local/dev/tests; {@code firebase} = Admin SDK verifyIdToken. */
        @NotBlank String mode,
        /**
         * Path to Firebase service-account JSON.
         * Empty → Application Default Credentials ({@code GOOGLE_APPLICATION_CREDENTIALS}).
         */
        String credentialsPath) {
}
