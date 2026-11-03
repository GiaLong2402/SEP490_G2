package com.hbsisms.feature.approval.dto;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.user.AppUser;
import java.time.Instant;

/** Matches FE {@code ApprovalListItem}. */
public record ApprovalListItemResponse(
        Long id,
        ApprovalType approvalType,
        String refType,
        Long refId,
        String title,
        ApprovalStatus status,
        Long requestedBy,
        Long decidedBy,
        Instant decidedAt,
        String note,
        Instant createdAt,
        String requesterName,
        String deciderName,
        String refLink) {

    public static ApprovalListItemResponse from(ApprovalTask task, String refLink) {
        AppUser requester = task.getRequestedBy();
        AppUser decider = task.getDecidedBy();
        return new ApprovalListItemResponse(
                task.getId(),
                task.getApprovalType(),
                task.getRefType(),
                task.getRefId(),
                task.getTitle(),
                task.getStatus(),
                requester == null ? null : requester.getId(),
                decider == null ? null : decider.getId(),
                task.getDecidedAt(),
                task.getNote(),
                task.getCreatedAt(),
                requester == null ? null : requester.getFullName(),
                decider == null ? null : decider.getFullName(),
                refLink);
    }
}
