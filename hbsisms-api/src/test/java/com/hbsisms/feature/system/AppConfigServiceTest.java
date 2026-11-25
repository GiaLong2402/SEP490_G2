package com.hbsisms.feature.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppConfigServiceTest {

    @Mock
    private AppConfigRepository appConfigRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private AppConfigService service;

    @BeforeEach
    void setUp() {
        service = new AppConfigService(appConfigRepository, userRepository, auditLogService, new ObjectMapper());
    }

    @Test
    void lowStockThreshold_usesDefaultWhenMissing() {
        when(appConfigRepository.findById(AppConfigService.LOW_STOCK_THRESHOLD)).thenReturn(Optional.empty());

        assertThat(service.lowStockThreshold()).isEqualTo(AppConfigService.DEFAULT_LOW_STOCK_THRESHOLD);
    }

    @Test
    void expiryWarningDays_readsConfiguredInt() {
        AppConfig row = new AppConfig();
        row.setKey(AppConfigService.EXPIRY_WARNING_DAYS);
        row.setValue("7");
        when(appConfigRepository.findById(AppConfigService.EXPIRY_WARNING_DAYS)).thenReturn(Optional.of(row));

        assertThat(service.expiryWarningDays()).isEqualTo(7);
    }

    @Test
    void getInt_fallsBackWhenValueInvalid() {
        AppConfig row = new AppConfig();
        row.setKey("x");
        row.setValue("abc");
        when(appConfigRepository.findById("x")).thenReturn(Optional.of(row));

        assertThat(service.getInt("x", 9)).isEqualTo(9);
    }

    @Test
    void listAll_mapsRows() {
        AppConfig row = new AppConfig();
        row.setKey(AppConfigService.EXPIRY_WARNING_DAYS);
        row.setValue("5");
        row.setValueType("int");
        row.setDescription("Số ngày cảnh báo");
        when(appConfigRepository.findAllByOrderByKeyAsc()).thenReturn(List.of(row));

        assertThat(service.listAll()).hasSize(1);
        assertThat(service.listAll().get(0).key()).isEqualTo(AppConfigService.EXPIRY_WARNING_DAYS);
    }

    @Test
    void update_rejectsInvalidInt() {
        AppConfig row = new AppConfig();
        row.setKey(AppConfigService.EXPIRY_WARNING_DAYS);
        row.setValueType("int");
        when(appConfigRepository.findById(AppConfigService.EXPIRY_WARNING_DAYS)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.update(AppConfigService.EXPIRY_WARNING_DAYS, "-1", 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("số nguyên");
    }

    @Test
    void update_savesAndAudits() {
        AppConfig row = new AppConfig();
        row.setKey(AppConfigService.EXPIRY_WARNING_DAYS);
        row.setValue("5");
        row.setValueType("int");
        when(appConfigRepository.findById(AppConfigService.EXPIRY_WARNING_DAYS)).thenReturn(Optional.of(row));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        service.update(AppConfigService.EXPIRY_WARNING_DAYS, "3", 1L);

        assertThat(row.getValue()).isEqualTo("3");
        verify(appConfigRepository).save(row);
        verify(auditLogService).record(eq(1L), eq("update"), eq("app_config"), isNull(), any());
    }
}
