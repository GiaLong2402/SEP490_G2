package com.hbsisms.feature.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CloudinaryProperties.class)
public class CloudinaryConfig {

    /** Used by server-side multipart upload; signature flow only needs properties. */
    @Bean
    Cloudinary cloudinary(CloudinaryProperties properties) {
        if (!properties.enabled()) {
            return new Cloudinary(ObjectUtils.asMap("cloud_name", "unconfigured"));
        }
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", properties.cloudName(),
                "api_key", properties.apiKey(),
                "api_secret", properties.apiSecret(),
                "secure", true));
    }
}
