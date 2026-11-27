package com.hbsisms.feature.costing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostingMovementServiceTest {

    @Mock
    private CostPeriodRepository periodRepository;
    @Mock
    private CostItemBalanceRepository balanceRepository;
    @Mock
    private CostRunningWacService runningWacService;

    private CostingMovementService service;

    @BeforeEach
    void setUp() {
        service = new CostingMovementService(periodRepository, balanceRepository, runningWacService);
    }

    @Test
    void inbound_withoutOpenPeriod_stillUpdatesRunning() {
        when(periodRepository.findByStatus(CostPeriodStatus.OPEN)).thenReturn(Optional.empty());

        service.recordInbound(
                CostItemType.FINISHED, 9L, new BigDecimal("6"), new BigDecimal("62823.04"), "receipt", 1L);

        verify(runningWacService)
                .applyInbound(
                        eq(CostItemType.FINISHED),
                        eq(9L),
                        eq(new BigDecimal("6")),
                        eq(new BigDecimal("62823.04")));
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void outbound_withoutOpenPeriod_stillUpdatesRunning() {
        when(periodRepository.findByStatus(CostPeriodStatus.OPEN)).thenReturn(Optional.empty());
        when(runningWacService.applyOutbound(CostItemType.RAW_MATERIAL, 3L, new BigDecimal("2")))
                .thenReturn(new BigDecimal("50000"));

        service.recordOutbound(CostItemType.RAW_MATERIAL, 3L, new BigDecimal("2"), "issue", 7L);

        verify(runningWacService).applyOutbound(CostItemType.RAW_MATERIAL, 3L, new BigDecimal("2"));
        verify(balanceRepository, never()).save(any());
    }

    @Test
    void inbound_withOpenPeriod_mirrorsBalance() {
        CostPeriod period = new CostPeriod();
        period.setStatus(CostPeriodStatus.OPEN);
        setId(period, 100L);
        when(periodRepository.findByStatus(CostPeriodStatus.OPEN)).thenReturn(Optional.of(period));

        AtomicReference<CostItemBalance> stored = new AtomicReference<>();
        when(balanceRepository.findForUpdate(100L, CostItemType.FINISHED, 9L))
                .thenAnswer(inv -> Optional.ofNullable(stored.get()));
        when(balanceRepository.saveAndFlush(any(CostItemBalance.class))).thenAnswer(inv -> {
            CostItemBalance b = inv.getArgument(0);
            setId(b, 55L);
            stored.set(b);
            return b;
        });
        when(balanceRepository.save(any(CostItemBalance.class))).thenAnswer(inv -> inv.getArgument(0));

        service.recordInbound(
                CostItemType.FINISHED, 9L, new BigDecimal("6"), new BigDecimal("100"), "receipt", 1L);

        verify(runningWacService)
                .applyInbound(CostItemType.FINISHED, 9L, new BigDecimal("6"), new BigDecimal("100"));
        ArgumentCaptor<CostItemBalance> cap = ArgumentCaptor.forClass(CostItemBalance.class);
        verify(balanceRepository).save(cap.capture());
        assertThat(cap.getValue().getInboundQty()).isEqualByComparingTo("6");
        assertThat(cap.getValue().getInboundValue()).isEqualByComparingTo("600");
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
