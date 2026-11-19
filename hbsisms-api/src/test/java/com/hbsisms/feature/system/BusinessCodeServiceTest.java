package com.hbsisms.feature.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class BusinessCodeServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    private BusinessCodeService service;
    private final AtomicLong seq = new AtomicLong(0);

    @BeforeEach
    void setUp() {
        service = new BusinessCodeService(
                jdbc, Clock.fixed(Instant.parse("2026-11-18T00:00:00Z"), ZoneOffset.UTC));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(inv -> seq.incrementAndGet());
    }

    @Test
    void nextSalesOrderCode_formatsYearlySequence() {
        assertThat(service.nextSalesOrderCode()).isEqualTo("SO-2026-0001");
        assertThat(service.nextSalesOrderCode()).isEqualTo("SO-2026-0002");
    }

    @Test
    void nextPaymentAndPayosCodes_useDistinctSequences() {
        assertThat(service.nextPaymentCode()).isEqualTo("TT-0001");
        assertThat(service.nextPayosOrderCode()).isEqualTo(2L);
        assertThat(service.nextDeliveryCode()).isEqualTo("DL-2026-0003");
        assertThat(service.nextReturnCode()).isEqualTo("RET-2026-0004");
    }
}
