package com.hbsisms.feature.system.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAppConfigRequest(@NotNull(message = "Giá trị là bắt buộc") String value) {}
