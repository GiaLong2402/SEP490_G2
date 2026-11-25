package com.hbsisms.feature.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of POST/PUT /raw-materials — qtyOnHand is not accepted on update. */
public record RawMaterialRequest(
        @NotBlank(message = "Vui lòng nhập mã")
                @Size(max = 40, message = "Mã tối đa 40 ký tự")
                String code,
        @NotBlank(message = "Vui lòng nhập tên")
                @Size(max = 160, message = "Tên tối đa 160 ký tự")
                String name,
        /** Preferred: master unit id. */
        Long unitId,
        /** Legacy free-text unit; used only when {@code unitId} is absent. */
        @Size(max = 20, message = "Đơn vị tối đa 20 ký tự") String unit,
        @DecimalMin(value = "0", message = "Mức đặt lại không được âm") BigDecimal reorderLevel,
        @NotNull(message = "Trạng thái là bắt buộc") Boolean isActive,
        /** Khu cất trong kho nguyên liệu; bắt buộc khi tạo mới, để trống khi sửa = giữ khu cũ. */
        Long storageLocationId) {
}
