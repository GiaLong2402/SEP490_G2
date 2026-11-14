package com.hbsisms.feature.goong;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(GoongProperties.class)
public class GoongConfig {

    @Bean
    @Qualifier("goongRestClient")
    RestClient goongRestClient(GoongProperties properties) {
        String baseUrl = StringUtils.hasText(properties.baseUrl())
                ? properties.baseUrl().replaceAll("/+$", "")
                : "https://rsapi.goong.io";
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}
