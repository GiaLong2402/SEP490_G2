package com.hbsisms.feature.system;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.system.dto.NotificationResponse;
import com.hbsisms.feature.user.AppUser;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc/ghi thông báo in-app cho user đang đăng nhập. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listMine(Long userId) {
        return notificationRepository.findTop30ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public void markRead(Long id, Long userId) {
        Notification n = notificationRepository
                .findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy thông báo"));
        if (!n.isRead()) {
            n.setRead(true);
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    /** Ghi 1 thông báo cho user; bỏ qua nếu user null. */
    @Transactional
    public void notifyUser(AppUser user, String type, String title, String body, String link) {
        if (user == null) {
            return;
        }
        Notification n = new Notification();
        n.setUser(user);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        n.setRead(false);
        notificationRepository.save(n);
    }
}
