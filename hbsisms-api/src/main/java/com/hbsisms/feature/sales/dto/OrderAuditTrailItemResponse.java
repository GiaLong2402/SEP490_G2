package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.system.AuditLog;
import java.time.Instant;
import java.util.Map;

/** Ai sửa đơn lúc nào — từ audit_log (Inter 3 dòng 6). */
public record OrderAuditTrailItemResponse(
        Long id,
        String action,
        Long userId,
        String actorName,
        Instant createdAt,
        Map<String, Object> detail) {

    public static OrderAuditTrailItemResponse from(AuditLog log) {
        var user = log.getUser();
        return new OrderAuditTrailItemResponse(
                log.getId(),
                log.getAction(),
                user == null ? null : user.getId(),
                user == null ? null : user.getFullName(),
                log.getCreatedAt(),
                log.getDetail());
    }
}
