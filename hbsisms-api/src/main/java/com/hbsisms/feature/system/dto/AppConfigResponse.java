package com.hbsisms.feature.system.dto;

import java.time.Instant;

/** Khớp FE {@code AppConfig}. */
public record AppConfigResponse(
        String key,
        String value,
        String valueType,
        String description,
        Long updatedBy,
        Instant updatedAt) {}
