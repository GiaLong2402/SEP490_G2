package com.hbsisms.feature.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CloudinarySignatureServiceTest {

    @Test
    void sign_matchesCloudinarySha1Convention() {
        Map<String, String> params = new TreeMap<>();
        params.put("folder", "products");
        params.put("timestamp", "1710000000");

        String signature = CloudinarySignatureService.sign(params, "test_secret");

        assertThat(signature).isEqualTo("4889e5edad631ee03e84b61bf00c4aea6f290d07");
    }
}
