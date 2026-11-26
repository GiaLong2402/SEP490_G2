package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepositPolicyTest {

    @Mock
    private AppConfigService appConfigService;

    private DepositPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new DepositPolicy(appConfigService);
        lenient().when(appConfigService.largeOrderQtyThreshold()).thenReturn(50);
        lenient().when(appConfigService.largeOrderValueThreshold()).thenReturn(5_000_000);
        lenient().when(appConfigService.depositPercent()).thenReturn(30);
        lenient().when(appConfigService.depositPercentMin()).thenReturn(20);
        lenient().when(appConfigService.depositPercentMax()).thenReturn(50);
        lenient().when(appConfigService.depositPaymentHours()).thenReturn(24);
    }

    @Test
    void isLargeOrder_byQty() {
        assertThat(policy.isLargeOrder(50, BigDecimal.valueOf(100_000))).isTrue();
        assertThat(policy.isLargeOrder(49, BigDecimal.valueOf(100_000))).isFalse();
    }

    @Test
    void isLargeOrder_byValue() {
        assertThat(policy.isLargeOrder(1, BigDecimal.valueOf(5_000_000))).isTrue();
        assertThat(policy.isLargeOrder(1, BigDecimal.valueOf(4_999_999))).isFalse();
    }

    @Test
    void calculateDepositAmount_roundsToVnd() {
        BigDecimal pct = policy.resolveDepositPercent(null);
        assertThat(pct).isEqualByComparingTo("30.00");
        assertThat(policy.calculateDepositAmount(new BigDecimal("1000000"), pct))
                .isEqualByComparingTo("300000.00");
    }

    @Test
    void resolveDepositPercent_rejectsOutOfRange() {
        assertThatThrownBy(() -> policy.resolveDepositPercent(new BigDecimal("10")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void depositDueAt_addsHours() {
        Instant from = Instant.parse("2026-11-26T00:00:00Z");
        assertThat(policy.depositDueAt(from)).isEqualTo(Instant.parse("2026-11-27T00:00:00Z"));
    }
}
