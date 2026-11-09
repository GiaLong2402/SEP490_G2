package com.hbsisms.feature.auth.firebase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hbsisms.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class StubFirebaseTokenVerifierTest {

    private final StubFirebaseTokenVerifier verifier = new StubFirebaseTokenVerifier();

    @Test
    void verify_validStubToken_returnsUidAndPhone() {
        VerifiedFirebaseToken token = verifier.verify("stub.uid-1.0901234567");
        assertThat(token.uid()).isEqualTo("uid-1");
        assertThat(token.phone()).isEqualTo("0901234567");
    }

    @Test
    void verify_invalidToken_returns400() {
        assertThatThrownBy(() -> verifier.verify("not-a-stub"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
