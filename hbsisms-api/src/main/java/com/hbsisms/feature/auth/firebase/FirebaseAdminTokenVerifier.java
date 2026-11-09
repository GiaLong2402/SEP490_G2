package com.hbsisms.feature.auth.firebase;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production verifier — Firebase Admin {@code verifyIdToken}.
 * Expects phone sign-in tokens (claim {@code phone_number}).
 */
@Component
@ConditionalOnProperty(prefix = "app.security.firebase", name = "mode", havingValue = "firebase")
public class FirebaseAdminTokenVerifier implements FirebaseTokenVerifier {

    private final FirebaseAuth firebaseAuth;

    public FirebaseAdminTokenVerifier(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public VerifiedFirebaseToken verify(String idToken) {
        String raw = Strings.trimToNull(idToken);
        if (raw == null) {
            throw ApiException.badRequest("idToken không hợp lệ");
        }
        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(raw);
            String phone = extractPhone(decoded);
            if (phone == null) {
                throw ApiException.badRequest("Token Firebase không có số điện thoại");
            }
            return new VerifiedFirebaseToken(decoded.getUid(), phone);
        } catch (FirebaseAuthException ex) {
            throw ApiException.badRequest("idToken Firebase không hợp lệ hoặc đã hết hạn");
        }
    }

    private static String extractPhone(FirebaseToken decoded) {
        Object claim = decoded.getClaims().get("phone_number");
        if (claim instanceof String s) {
            return Strings.trimToNull(s);
        }
        return null;
    }
}
