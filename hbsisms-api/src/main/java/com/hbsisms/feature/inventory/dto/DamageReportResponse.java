package com.hbsisms.feature.inventory.dto;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.inventory.DamageReport;
import java.time.Instant;

/** Matches FE {@code DamageListItem}. */
public record DamageReportResponse(
        Long id,
        String code,
        Long variantId,
        Long lotId,
        Long locationId,
        int qty,
        String reason,
        ApprovalStatus status,
        Long reportedBy,
        Long reviewedBy,
        Instant reportedAt,
        Instant reviewedAt,
        String note,
        String sku,
        String productName,
        String lotCode,
        String locationCode,
        String reporterName,
        String reviewerName) {

    public static DamageReportResponse from(DamageReport report, boolean multiWarehouse) {
        String locationCode = null;
        if (report.getLocation() != null) {
            locationCode = multiWarehouse
                    ? report.getLocation().getWarehouse().getCode() + "/" + report.getLocation().getCode()
                    : report.getLocation().getCode();
        }
        return new DamageReportResponse(
                report.getId(),
                report.getCode(),
                report.getVariant().getId(),
                report.getLot() == null ? null : report.getLot().getId(),
                report.getLocation() == null ? null : report.getLocation().getId(),
                report.getQty(),
                report.getReason(),
                report.getStatus(),
                report.getReportedBy() == null ? null : report.getReportedBy().getId(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getId(),
                report.getReportedAt(),
                report.getReviewedAt(),
                report.getNote(),
                report.getVariant().getSku(),
                report.getVariant().getProduct().getName(),
                report.getLot() == null ? null : report.getLot().getLotCode(),
                locationCode,
                report.getReportedBy() == null ? null : report.getReportedBy().getFullName(),
                report.getReviewedBy() == null ? null : report.getReviewedBy().getFullName());
    }
}
