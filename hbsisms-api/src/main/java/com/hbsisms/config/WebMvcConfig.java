package com.hbsisms.config;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.customer.CustomerType;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Query/path params use lowercase codes ({@code pending}, {@code b2b}) matching FE —
 * Jackson {@code @JsonCreator} alone does not apply to {@code @RequestParam}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, ApprovalStatus.class, ApprovalStatus::fromCode);
        registry.addConverter(String.class, ApprovalType.class, source ->
                source == null || source.isBlank() ? null : ApprovalType.fromCode(source));
        registry.addConverter(String.class, CustomerType.class, source ->
                source == null || source.isBlank() ? null : CustomerType.fromCode(source));
    }
}
