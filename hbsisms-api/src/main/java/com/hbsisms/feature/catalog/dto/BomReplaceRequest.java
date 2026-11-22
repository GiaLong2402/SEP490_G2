package com.hbsisms.feature.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Body of PUT /products/{id}/bom — full replace of BOM lines. */
public record BomReplaceRequest(
        @NotNull(message = "Danh sách định mức là bắt buộc")
        @Valid
        List<BomLineRequest> lines) {
}
