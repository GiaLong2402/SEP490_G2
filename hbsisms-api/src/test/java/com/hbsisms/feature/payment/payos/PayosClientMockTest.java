package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PayosClientMockTest {

    private MockRestServiceServer server;
    private PayosClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api-merchant.payos.vn");
        server = MockRestServiceServer.bindTo(builder).build();
        PayosProperties props = new PayosProperties(
                "sandbox",
                "client-id",
                "api-key",
                "checksum",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");
        client = new PayosClient(builder.build(), props, new ObjectMapper());
    }

    @Test
    void createPaymentRequest_parsesSuccessPayload() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PayosConfig.HEADER_CLIENT_ID, "client-id"))
                .andExpect(header(PayosConfig.HEADER_API_KEY, "api-key"))
                .andRespond(withSuccess(
                        """
                        {"code":"00","desc":"success","data":{
                          "orderCode":130001,
                          "amount":2000,
                          "checkoutUrl":"https://pay.payos.vn/web/xyz",
                          "qrCode":"000201",
                          "paymentLinkId":"plid",
                          "status":"PENDING"
                        }}
                        """,
                        MediaType.APPLICATION_JSON));

        PayosPaymentLinkResult result =
                client.createPaymentRequest(130001L, 2000, "TEST", InstantEpoch.nowPlus15());

        assertThat(result.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/xyz");
        assertThat(result.qrCode()).isEqualTo("000201");
        assertThat(result.paymentLinkId()).isEqualTo("plid");
        server.verify();
    }

    /** Avoid Instant import noise in expect — epoch only. */
    private static final class InstantEpoch {
        static long nowPlus15() {
            return java.time.Instant.now().plusSeconds(900).getEpochSecond();
        }
    }
}
