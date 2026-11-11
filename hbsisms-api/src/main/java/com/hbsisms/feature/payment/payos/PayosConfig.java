package com.hbsisms.feature.payment.payos;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * PayOS Spring wiring (task #32 config + #33 RestClient).
 * Header names match <a href="https://payos.vn/docs/api/">payOS API docs</a>.
 */
@Configuration
@EnableConfigurationProperties(PayosProperties.class)
public class PayosConfig {

    /** Request header for merchant Client ID. */
    public static final String HEADER_CLIENT_ID = "x-client-id";

    /** Request header for merchant API Key. */
    public static final String HEADER_API_KEY = "x-api-key";

    @Bean
    @Qualifier("payosRestClient")
    RestClient payosRestClient(PayosProperties properties) {
        String baseUrl = StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().replaceAll("/+$", "")
                : "https://api-merchant.payos.vn";
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
