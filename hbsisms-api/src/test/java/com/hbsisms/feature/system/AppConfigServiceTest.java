package com.hbsisms.feature.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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

    private AppConfigService service;

    @BeforeEach
    void setUp() {
        service = new AppConfigService(appConfigRepository);
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
}
