package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PayosPropertiesTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(PayosConfig.class);

    @Test
    void bindsFromAppPaymentPayosPrefix_andEnabledWhenSandbox() {
        contextRunner
                .withPropertyValues(
                        "app.payment.payos.mode=sandbox",
                        "app.payment.payos.client-id=client-1",
                        "app.payment.payos.api-key=api-1",
                        "app.payment.payos.checksum-key=checksum-1",
                        "app.payment.payos.base-url=https://api-merchant.payos.vn",
                        "app.payment.payos.return-url=http://localhost:5173/thanh-toan/ket-qua",
                        "app.payment.payos.cancel-url=http://localhost:5173/thanh-toan/ket-qua")
                .run(ctx -> {
                    PayosProperties props = ctx.getBean(PayosProperties.class);
                    assertThat(props.mode()).isEqualTo("sandbox");
                    assertThat(props.clientId()).isEqualTo("client-1");
                    assertThat(props.apiKey()).isEqualTo("api-1");
                    assertThat(props.checksumKey()).isEqualTo("checksum-1");
                    assertThat(props.enabled()).isTrue();
                    assertThat(PayosConfig.HEADER_CLIENT_ID).isEqualTo("x-client-id");
                    assertThat(PayosConfig.HEADER_API_KEY).isEqualTo("x-api-key");
                });
    }

    @Test
    void enabled_modeOff_returnsFalse() {
        PayosProperties props = new PayosProperties(
                "off",
                "client",
                "api",
                "checksum",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");

        assertThat(props.enabled()).isFalse();
    }

    @Test
    void enabled_sandboxWithAllKeys_returnsTrue() {
        PayosProperties props = new PayosProperties(
                "sandbox",
                "14bc8c52-test",
                "9d241141-test",
                "checksum-hex",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");

        assertThat(props.enabled()).isTrue();
    }

    @Test
    void enabled_sandboxMissingKey_returnsFalse() {
        PayosProperties props = new PayosProperties(
                "sandbox",
                "client",
                "api",
                "",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");

        assertThat(props.enabled()).isFalse();
    }

    @Test
    void enabled_liveWithAllKeys_returnsTrue() {
        PayosProperties props = new PayosProperties(
                "live",
                "client",
                "api",
                "checksum",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");

        assertThat(props.enabled()).isTrue();
    }
}
