package com.hbsisms.feature.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** Bound from {@code app.cloudinary.*} / {@code CLOUDINARY_*} env. */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(
        String cloudName, String apiKey, String apiSecret, String folder) {

    public boolean enabled() {
        return StringUtils.hasText(cloudName)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret);
    }

    public String resolvedFolder() {
        return StringUtils.hasText(folder) ? folder.trim() : "products";
    }
}
