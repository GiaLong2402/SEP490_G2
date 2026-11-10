package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Live sandbox smoke when {@code PAYOS_MODE=sandbox} and keys are loaded from {@code .env}.
 * Skips automatically when PayOS is off (CI without secrets).
 */
@SpringBootTest
@ActiveProfiles("it-external")
class PayosSandboxCreateIT {

    static {
        System.setProperty("user.timezone", "UTC");
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
    }

    @Autowired
    private PayosProperties properties;
    @Autowired
    private PayosClient payosClient;

    @Test
    void createPaymentRequest_sandboxReturnsCheckoutUrl() {
        assumeTrue(properties.enabled(), "PayOS sandbox not configured — skip live call");

        long orderCode = 800_000_000L + (System.currentTimeMillis() % 99_000_000L);
        long expiredAt = Instant.now().plusSeconds(900).getEpochSecond();

        PayosPaymentLinkResult result =
                payosClient.createPaymentRequest(orderCode, 2000, "TEST33", expiredAt);

        assertThat(result.checkoutUrl()).contains("payos");
        assertThat(result.orderCode()).isEqualTo(orderCode);
    }
}
