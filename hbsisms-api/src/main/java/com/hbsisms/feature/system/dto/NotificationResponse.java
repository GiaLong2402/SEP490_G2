package com.hbsisms.feature.system.dto;

import com.hbsisms.feature.system.Notification;
import java.time.Instant;

/** Body GET /notifications — khớp FE internal/external chuông. */
public record NotificationResponse(
        Long id,
        Long userId,
        String type,
        String title,
        String body,
        String link,
        boolean isRead,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUser() == null ? null : n.getUser().getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.isRead(),
                n.getCreatedAt());
    }
}
