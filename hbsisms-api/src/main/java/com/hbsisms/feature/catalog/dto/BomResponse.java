package com.hbsisms.feature.catalog.dto;

import java.util.List;

public record BomResponse(Long productId, String productCode, String productName, List<BomLineResponse> lines) {
}
