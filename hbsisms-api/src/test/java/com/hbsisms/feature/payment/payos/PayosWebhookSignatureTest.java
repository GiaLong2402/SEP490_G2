package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PayosWebhookSignatureTest {

    /** Official sample from payOS webhook signature docs. */
    private static final String DOCS_CHECKSUM =
            "1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675";
    private static final String DOCS_SIGNATURE =
            "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";

    @Test
    void verifyWebhook_matchesOfficialDocsVector() {
        Map<String, Object> data = docsSampleData();
        assertThat(PayosSignature.verifyWebhook(data, DOCS_SIGNATURE, DOCS_CHECKSUM)).isTrue();
        assertThat(PayosSignature.signDataObject(data, DOCS_CHECKSUM)).isEqualTo(DOCS_SIGNATURE);
    }

    @Test
    void verifyWebhook_rejectsTamperedSignature() {
        assertThat(PayosSignature.verifyWebhook(docsSampleData(), "deadbeef", DOCS_CHECKSUM)).isFalse();
    }

    @Test
    void verifyWebhook_rejectsTamperedAmount() {
        Map<String, Object> data = docsSampleData();
        data.put("amount", 9999);
        assertThat(PayosSignature.verifyWebhook(data, DOCS_SIGNATURE, DOCS_CHECKSUM)).isFalse();
    }

    private static Map<String, Object> docsSampleData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderCode", 123);
        data.put("amount", 3000);
        data.put("description", "VQRIO123");
        data.put("accountNumber", "12345678");
        data.put("reference", "TF230204212323");
        data.put("transactionDateTime", "2023-02-04 18:25:00");
        data.put("currency", "VND");
        data.put("paymentLinkId", "124c33293c43417ab7879e14c8d9eb18");
        data.put("code", "00");
        data.put("desc", "Thành công");
        data.put("counterAccountBankId", "");
        data.put("counterAccountBankName", "");
        data.put("counterAccountName", "");
        data.put("counterAccountNumber", "");
        data.put("virtualAccountName", "");
        data.put("virtualAccountNumber", "");
        return data;
    }
}
