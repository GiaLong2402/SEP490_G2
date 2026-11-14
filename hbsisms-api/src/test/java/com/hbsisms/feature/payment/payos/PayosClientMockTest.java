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

    @Test
    void getPaymentRequest_parsesPaidStatus() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130001"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(PayosConfig.HEADER_CLIENT_ID, "client-id"))
                .andExpect(header(PayosConfig.HEADER_API_KEY, "api-key"))
                .andRespond(withSuccess(
                        """
                        {"code":"00","desc":"success","data":{
                          "orderCode":130001,
                          "amount":2000,
                          "status":"PAID",
                          "paymentLinkId":"plid"
                        }}
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client.getPaymentRequest(130001L);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("PAID");
        assertThat(result.get().amount()).isEqualTo(2000);
        assertThat(result.get().amountPaid()).isNull();
        assertThat(result.get().received()).isEqualTo(2000);
        server.verify();
    }

    @Test
    void getPaymentRequest_readsAmountPaid() {
        // Đơn 2.000đ, khách sửa số tiền chuyển 10.000đ
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130058"))
                .andRespond(withSuccess(
                        """
                        {"code":"00","desc":"success","data":{
                          "orderCode":130058,"amount":2000,"amountPaid":10000,"amountRemaining":0,
                          "status":"PAID","paymentLinkId":"plid"
                        }}
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client.getPaymentRequest(130058L);

        assertThat(result.get().amount()).isEqualTo(2000);
        assertThat(result.get().received()).isEqualTo(10000);
        server.verify();
    }

    @Test
    void getPaymentRequest_sumsTransactionsWhenNoAmountPaid() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130059"))
                .andRespond(withSuccess(
                        """
                        {"code":"00","desc":"success","data":{
                          "orderCode":130059,"amount":2000,"status":"PAID",
                          "transactions":[{"amount":6000},{"amount":4000}]
                        }}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(client.getPaymentRequest(130059L).get().received()).isEqualTo(10000);
        server.verify();
    }

    @Test
    void getPaymentRequest_non00_returnsEmpty() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"code":"01","desc":"fail","data":null}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(client.getPaymentRequest(130001L)).isEmpty();
        server.verify();
    }

    @Test
    void getPaymentRequest_code101_returnsNotFound() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130048"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"code":"101","desc":"Mã thanh toán không tồn tại","data":null}
                        """,
                        MediaType.APPLICATION_JSON));

        var result = client.getPaymentRequest(130048L);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo("NOT_FOUND");
        assertThat(result.get().orderCode()).isEqualTo(130048L);
        server.verify();
    }

    @Test
    void cancelPaymentRequest_returnsTrueOnCode00() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/plink/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PayosConfig.HEADER_CLIENT_ID, "client-id"))
                .andExpect(header(PayosConfig.HEADER_API_KEY, "api-key"))
                .andRespond(withSuccess(
                        """
                        {"code":"00","desc":"success","data":{"status":"CANCELLED"}}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(client.cancelPaymentRequest("plink", "Hết hạn thanh toán")).isTrue();
        server.verify();
    }

    @Test
    void cancelPaymentRequest_non00_returnsFalse() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/42/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {"code":"231","desc":"already cancelled","data":null}
                        """,
                        MediaType.APPLICATION_JSON));

        assertThat(client.cancelPaymentRequest("42", "reason")).isFalse();
        server.verify();
    }

    @Test
    void getPaymentRequest_httpError_returnsEmpty() {
        // PayOS lỗi 500 → coi như "không hỏi được", để job / trang kết quả hoãn chứ không hủy đơn
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/130001"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThat(client.getPaymentRequest(130001L)).isEmpty();
        server.verify();
    }

    @Test
    void cancelPaymentRequest_httpError_returnsFalse() {
        server.expect(requestTo("https://api-merchant.payos.vn/v2/payment-requests/plink/cancel"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withServerError());

        assertThat(client.cancelPaymentRequest("plink", "Khách hủy đơn")).isFalse();
        server.verify();
    }

    @Test
    void cancelPaymentRequest_blankId_skipsCall() {
        assertThat(client.cancelPaymentRequest(" ", "x")).isFalse();
        server.verify(); // không có request nào
    }

    /** Avoid Instant import noise in expect — epoch only. */
    private static final class InstantEpoch {
        static long nowPlus15() {
            return java.time.Instant.now().plusSeconds(900).getEpochSecond();
        }
    }
}
