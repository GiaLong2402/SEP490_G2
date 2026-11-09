package com.hbsisms.feature.auth.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.hbsisms.common.text.Strings;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.security.firebase", name = "mode", havingValue = "firebase")
public class FirebaseAdminConfig {

    @Bean
    FirebaseApp firebaseApp(FirebaseAuthProperties properties) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials = loadCredentials(properties.credentialsPath());
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private static GoogleCredentials loadCredentials(String credentialsPath) throws IOException {
        String path = Strings.trimToNull(credentialsPath);
        if (path == null) {
            return GoogleCredentials.getApplicationDefault();
        }
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "FIREBASE_CREDENTIALS_PATH không tồn tại hoặc không phải file: " + file.toAbsolutePath());
        }
        try (InputStream in = Files.newInputStream(file)) {
            return GoogleCredentials.fromStream(in);
        }
    }
}
