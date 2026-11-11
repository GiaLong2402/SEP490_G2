package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayosSignatureTest {

    @Test
    void signPaymentRequest_isStableHex() {
        String first = PayosSignature.signPaymentRequest(
                10000,
                "http://localhost:5173/thanh-toan/ket-qua",
                "DONHANG01",
                123456,
                "http://localhost:5173/thanh-toan/ket-qua",
                "checksum-key-for-test");
        String second = PayosSignature.signPaymentRequest(
                10000,
                "http://localhost:5173/thanh-toan/ket-qua",
                "DONHANG01",
                123456,
                "http://localhost:5173/thanh-toan/ket-qua",
                "checksum-key-for-test");

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    void signPaymentRequest_usesAlphabeticallyOrderedFields() {
        String expected = PayosSignature.hmacHex(
                "amount=2000&cancelUrl=https://a/cancel&description=abc&orderCode=1&returnUrl=https://a/ok",
                "secret");
        String signature =
                PayosSignature.signPaymentRequest(2000, "https://a/cancel", "abc", 1L, "https://a/ok", "secret");
        assertThat(signature).isEqualTo(expected);
    }
}
