package com.hbsisms.feature.system.dto;

import com.hbsisms.feature.system.AuditLog;
import java.time.Instant;
import java.util.Map;

/** Khớp FE {@code AuditRow}. {@code entityCode} = mã nghiệp vụ (PN-…, SO-…), null nếu không có. */
public record AuditLogResponse(
        Long id,
        Long userId,
        String userName,
        String action,
        String entityType,
        Long entityId,
        String entityCode,
        Map<String, Object> detail,
        String ipAddress,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog row) {
        return from(row, null);
    }

    public static AuditLogResponse from(AuditLog row, String entityCode) {
        var user = row.getUser();
        return new AuditLogResponse(
                row.getId(),
                user == null ? null : user.getId(),
                user == null ? null : user.getFullName(),
                row.getAction(),
                row.getEntityType(),
                row.getEntityId(),
                entityCode,
                row.getDetail(),
                row.getIpAddress(),
                row.getCreatedAt());
    }
}
