package com.hbsisms.feature.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Body of POST/PUT variant — matches FE {@code VariantInput}. */
public record VariantRequest(
        @NotBlank(message = "Vui lòng nhập SKU")
        @Size(max = 60, message = "SKU tối đa 60 ký tự")
        String sku,

        @NotBlank(message = "Vui lòng nhập quy cách")
        @Size(max = 80, message = "Quy cách tối đa 80 ký tự")
        String packLabel,

        Integer unitWeightG,

        @NotNull(message = "Vui lòng nhập giá niêm yết")
        @DecimalMin(value = "0.01", message = "Giá niêm yết phải lớn hơn 0")
        BigDecimal listPrice,

        @NotNull(message = "Vui lòng nhập số khay / thùng")
        @Min(value = 1, message = "Số khay / thùng phải từ 1 trở lên")
        Integer packPerCase,

        @Size(max = 50, message = "Mã vạch tối đa 50 ký tự")
        String barcode,

        @NotNull(message = "Trạng thái kinh doanh là bắt buộc")
        Boolean isActive,

        @Valid List<TierInput> tiers) {
}
