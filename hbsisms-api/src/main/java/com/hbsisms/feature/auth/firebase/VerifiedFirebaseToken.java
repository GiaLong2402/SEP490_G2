package com.hbsisms.feature.auth.firebase;

/**
 * Claims extracted after verifying a Firebase ID token (or stub token in local/dev).
 *
 * @param uid   Firebase {@code sub}
 * @param phone E.164 or local VN digits as returned by the verifier (normalized later for DB match)
 */
public record VerifiedFirebaseToken(String uid, String phone) {
}
