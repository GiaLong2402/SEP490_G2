package com.hbsisms.feature.system;

import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import org.springframework.stereotype.Service;

/** Ghi thông báo cho mọi tài khoản Chủ đang hoạt động. */
@Service
public class OwnerNotifier {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    public OwnerNotifier(UserRepository userRepository, NotificationRepository notificationRepository) {
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
    }

    /** @return số thông báo đã ghi */
    public int notifyOwners(String type, String title, String body, String link) {
        int count = 0;
        for (AppUser owner : userRepository.findByRole_CodeAndActiveTrue(DefaultRoles.OWNER)) {
            Notification n = new Notification();
            n.setUser(owner);
            n.setType(type);
            n.setTitle(title);
            n.setBody(body);
            n.setLink(link);
            n.setRead(false);
            notificationRepository.save(n);
            count++;
        }
        return count;
    }
}
