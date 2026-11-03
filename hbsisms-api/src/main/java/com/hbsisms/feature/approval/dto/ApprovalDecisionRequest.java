package com.hbsisms.feature.approval.dto;

import com.hbsisms.feature.approval.ApprovalStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST /approvals/{id}/decision. Matches FE {@code ApprovalDecision}. */
public record ApprovalDecisionRequest(
        @NotNull(message = "decision là bắt buộc")
        ApprovalStatus decision,

        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String note) {
}
