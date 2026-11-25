package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.SellUnit;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Một dòng hàng trên yêu cầu (khay hoặc thùng). */
public record SalesRequestLineInput(
        @NotNull Long variantId,
        @NotNull SellUnit unit,
        @Min(value = 1, message = "Số lượng phải lớn hơn 0") int qty) {}
