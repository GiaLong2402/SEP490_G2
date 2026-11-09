package com.hbsisms.feature.auth.firebase;

import com.hbsisms.common.exception.ApiException;

/** Verifies a Firebase Auth ID token (stub or Admin SDK depending on {@code app.security.firebase.mode}). */
public interface FirebaseTokenVerifier {

    /**
     * @throws ApiException 400 when the token is missing, malformed, expired, or not trusted
     */
    VerifiedFirebaseToken verify(String idToken);
}
