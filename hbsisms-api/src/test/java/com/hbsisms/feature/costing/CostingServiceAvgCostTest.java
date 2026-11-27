package com.hbsisms.feature.costing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.system.AuditLogService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CostingServiceAvgCostTest {

    @Mock
    private CostPeriodRepository periodRepository;
    @Mock
    private CostItemBalanceRepository balanceRepository;
    @Mock
    private CostRunningBalanceRepository runningBalanceRepository;
    @Mock
    private CostRunningWacService runningWacService;
    @Mock
    private RawMaterialLotRepository rawMaterialLotRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private AuditLogService auditLogService;

    private CostingService costingService;

    @BeforeEach
    void setUp() {
        costingService = new CostingService(
                periodRepository,
                balanceRepository,
                runningBalanceRepository,
                runningWacService,
                rawMaterialLotRepository,
                rawMaterialRepository,
                stockRepository,
                productVariantRepository,
                auditLogService);
    }

    @Test
    void currentRawAvg_readsRunningFirst_evenWithoutPeriod() {
        when(runningWacService.currentAvgUnitCost(CostItemType.RAW_MATERIAL, 4L))
                .thenReturn(Optional.of(new BigDecimal("12345.67")));

        assertThat(costingService.currentRawAvgUnitCost(4L)).contains(new BigDecimal("12345.67"));
        verifyNoInteractions(periodRepository);
    }

    @Test
    void currentFinishedAvg_fallsBackToPeriodAndSeedsRunning() {
        when(runningWacService.currentAvgUnitCost(CostItemType.FINISHED, 8L)).thenReturn(Optional.empty());
        CostPeriod open = new CostPeriod();
        open.setStatus(CostPeriodStatus.OPEN);
        setId(open, 1L);
        when(periodRepository.findByStatus(CostPeriodStatus.OPEN)).thenReturn(Optional.of(open));

        CostItemBalance bal =
                CostItemBalance.opening(open, CostItemType.FINISHED, 8L, new BigDecimal("79"), new BigDecimal("14736720.22"));
        when(balanceRepository.findByPeriod_IdAndItemTypeAndItemId(1L, CostItemType.FINISHED, 8L))
                .thenReturn(Optional.of(bal));

        assertThat(costingService.currentFinishedAvgUnitCost(8L)).contains(new BigDecimal("186540.76"));
        verify(runningWacService)
                .ensurePresent(
                        CostItemType.FINISHED,
                        8L,
                        new BigDecimal("79.000"),
                        new BigDecimal("14736720.22"));
    }

    @Test
    void resolveRawIssue_usesLotWhenNoWac() {
        when(runningWacService.currentAvgUnitCost(CostItemType.RAW_MATERIAL, 2L)).thenReturn(Optional.empty());
        when(periodRepository.findByStatus(CostPeriodStatus.OPEN)).thenReturn(Optional.empty());
        when(periodRepository.findFirstByStatusOrderByPeriodStartDesc(CostPeriodStatus.CLOSED))
                .thenReturn(Optional.empty());

        assertThat(costingService.resolveRawIssueUnitCost(2L, new BigDecimal("9000")))
                .isEqualByComparingTo("9000");
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