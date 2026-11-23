package com.hbsisms.feature.system;

import com.hbsisms.feature.system.dto.NotificationResponse;
import com.hbsisms.security.AuthUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Chuông thông báo — mọi role đã login (staff + customer). */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AuthUser actor) {
        return notificationService.listMine(requireUserId(actor));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        notificationService.markRead(id, requireUserId(actor));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal AuthUser actor) {
        notificationService.markAllRead(requireUserId(actor));
    }

    private static Long requireUserId(AuthUser actor) {
        if (actor == null || actor.id() == null) {
            throw com.hbsisms.common.exception.ApiException.forbidden("Cần đăng nhập");
        }
        return actor.id();
    }
}
