package com.hbsisms.feature.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepository, userRepository);
    }

    @Test
    void record_savesLogWithUserAndDetail() {
        AppUser user = new AppUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("qtyDelta", 3);
        detail.put("note", "Bù thiếu đếm");

        auditLogService.record(1L, "adjust", "stock", 10L, detail);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getAction()).isEqualTo("adjust");
        assertThat(saved.getEntityType()).isEqualTo("stock");
        assertThat(saved.getEntityId()).isEqualTo(10L);
        assertThat(saved.getDetail()).containsEntry("qtyDelta", 3).containsEntry("note", "Bù thiếu đếm");
    }

    @Test
    void record_allowsNullUser() {
        auditLogService.record(null, "adjust", "stock", 10L, Map.of("qtyDelta", -1));

        verify(userRepository, never()).findById(any());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isNull();
        assertThat(captor.getValue().getAction()).isEqualTo("adjust");
        assertThat(captor.getValue().getEntityId()).isEqualTo(10L);
    }
}
