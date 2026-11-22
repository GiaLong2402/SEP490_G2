package com.hbsisms.feature.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudinaryConfig {

    /** Always registered; {@link CloudinaryStorageService} checks {@link CloudinaryProperties#enabled()}. */
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
