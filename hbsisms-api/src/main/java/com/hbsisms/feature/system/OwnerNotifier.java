package com.hbsisms.feature.system;

import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import org.springframework.stereotype.Service;

/** Ghi thông báo cho mọi tài khoản Chủ đang hoạt động. */
@Service
public class OwnerNotifier {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public OwnerNotifier(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** @return số thông báo đã ghi */
    public int notifyOwners(String type, String title, String body, String link) {
        int count = 0;
        for (AppUser owner : userRepository.findByRole_CodeAndActiveTrue(DefaultRoles.OWNER)) {
            notificationService.notifyUser(owner, type, title, body, link);
            count++;
        }
        return count;
    }
}
