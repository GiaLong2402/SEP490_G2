package com.hbsisms.feature.payment.payos;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * PayOS Spring wiring (task #32). Header names match
 * <a href="https://payos.vn/docs/api/">payOS API docs</a>.
 * No RestClient / payment-request creation here (#33).
 */
@Configuration
@EnableConfigurationProperties(PayosProperties.class)
public class PayosConfig {

    /** Request header for merchant Client ID. */
    public static final String HEADER_CLIENT_ID = "x-client-id";

    /** Request header for merchant API Key. */
    public static final String HEADER_API_KEY = "x-api-key";
}
