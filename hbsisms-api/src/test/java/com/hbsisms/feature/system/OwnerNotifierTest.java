package com.hbsisms.feature.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Báo Chủ cửa hàng (lệch tiền, cần hoàn tiền) — lưu bảng notification. */
@ExtendWith(MockitoExtension.class)
class OwnerNotifierTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void notifyOwners_writesOnePerActiveOwner() {
        AppUser a = new AppUser();
        AppUser b = new AppUser();
        when(userRepository.findByRole_CodeAndActiveTrue(DefaultRoles.OWNER)).thenReturn(List.of(a, b));

        int count = new OwnerNotifier(userRepository, notificationRepository)
                .notifyOwners("payment_overpaid", "Tiêu đề", "Nội dung", "/orders/9");

        assertThat(count).isEqualTo(2);
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getUser).containsExactly(a, b);
        Notification first = saved.getAllValues().get(0);
        assertThat(first.getType()).isEqualTo("payment_overpaid");
        assertThat(first.getTitle()).isEqualTo("Tiêu đề");
        assertThat(first.getBody()).isEqualTo("Nội dung");
        assertThat(first.getLink()).isEqualTo("/orders/9");
        assertThat(first.isRead()).isFalse();
    }

    @Test
    void notifyOwners_noOwner_writesNothing() {
        when(userRepository.findByRole_CodeAndActiveTrue(DefaultRoles.OWNER)).thenReturn(List.of());

        int count = new OwnerNotifier(userRepository, notificationRepository)
                .notifyOwners("payment_refund", "t", "b", "/payments");

        assertThat(count).isZero();
        verify(notificationRepository, never()).save(any());
    }
}
