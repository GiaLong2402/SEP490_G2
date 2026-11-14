package com.hbsisms.feature.goong;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Goong Maps geocode + shop location for delivery radius. Bound from {@code app.goong.*}. */
@ConfigurationProperties(prefix = "app.goong")
public record GoongProperties(
        String apiKey,
        String baseUrl,
        BigDecimal shopLatitude,
        BigDecimal shopLongitude,
        BigDecimal deliveryRadiusKm) {

    public boolean enabled() {
        return StringUtils.hasText(apiKey);
    }
}
