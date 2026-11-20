package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.ReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Body of PATCH /returns/{id}/status.
 *
 * <p>Khi {@code status=received}: có thể gửi {@code inspections} (cộng tồn tại {@code locationId}).
 * Bỏ trống thì mặc định cộng tồn về vị trí đã xuất của từng dòng.
 */
public record ReturnStatusRequest(
        @NotNull(message = "status là bắt buộc") ReturnStatus status,
        @Valid List<ReturnInspectLine> inspections) {

    public ReturnStatusRequest(ReturnStatus status) {
        this(status, null);
    }
}
