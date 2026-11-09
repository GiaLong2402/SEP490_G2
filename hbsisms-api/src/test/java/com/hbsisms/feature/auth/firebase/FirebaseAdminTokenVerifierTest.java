package com.hbsisms.feature.auth.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.hbsisms.common.exception.ApiException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FirebaseAdminTokenVerifierTest {

    private FirebaseAuth firebaseAuth;
    private FirebaseAdminTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        firebaseAuth = mock(FirebaseAuth.class);
        verifier = new FirebaseAdminTokenVerifier(firebaseAuth);
    }

    @Test
    void verify_validPhoneToken_returnsUidAndPhone() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid-1");
        when(token.getClaims()).thenReturn(Map.of("phone_number", "+84901234567"));
        when(firebaseAuth.verifyIdToken("good-jwt")).thenReturn(token);

        VerifiedFirebaseToken verified = verifier.verify("good-jwt");

        assertThat(verified.uid()).isEqualTo("firebase-uid-1");
        assertThat(verified.phone()).isEqualTo("+84901234567");
    }

    @Test
    void verify_missingPhoneClaim_returns400() throws Exception {
        FirebaseToken token = mock(FirebaseToken.class);
        when(token.getUid()).thenReturn("firebase-uid-1");
        when(token.getClaims()).thenReturn(Map.of());
        when(firebaseAuth.verifyIdToken(anyString())).thenReturn(token);

        assertThatThrownBy(() -> verifier.verify("no-phone"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("số điện thoại");
    }

    @Test
    void verify_firebaseRejectsToken_returns400() throws Exception {
        when(firebaseAuth.verifyIdToken(anyString()))
                .thenThrow(mock(FirebaseAuthException.class));

        assertThatThrownBy(() -> verifier.verify("bad"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không hợp lệ");
    }
}
