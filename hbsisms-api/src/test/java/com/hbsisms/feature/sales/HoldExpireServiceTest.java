package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.payment.payos.PayosOrderExpireHandler;
import com.hbsisms.feature.system.AppConfigService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldExpireServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T04:20:00Z");

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private PayosOrderExpireHandler payosOrderExpireHandler;
    @Mock
    private AppConfigService appConfigService;

    /** Job chỉ lấy đơn quá giờ giữ hàng + 2 phút ân hạn. */
    private static final Instant CUTOFF = NOW.minusSeconds(120);

    private HoldExpireService newService() {
        when(appConfigService.paymentGraceMinutes()).thenReturn(2);
        return new HoldExpireService(
                salesOrderRepository, payosOrderExpireHandler, appConfigService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void run_processesEachExpiredOrderViaHandler() {
        SalesOrder header = new SalesOrder();
        setId(header, 9L);
        SalesOrder detailed = new SalesOrder();
        setId(detailed, 9L);
        when(salesOrderRepository.findExpiredHolds(CUTOFF)).thenReturn(List.of(header));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(detailed));
        when(payosOrderExpireHandler.expireIfNeeded(detailed)).thenReturn(true);

        HoldExpireService service = newService();

        assertThat(service.run().releasedOrders()).isEqualTo(1);
        verify(payosOrderExpireHandler).expireIfNeeded(detailed);
    }

    @Test
    void run_countsOnlyResolvedOrders() {
        SalesOrder a = new SalesOrder();
        setId(a, 1L);
        SalesOrder b = new SalesOrder();
        setId(b, 2L);
        when(salesOrderRepository.findExpiredHolds(CUTOFF)).thenReturn(List.of(a, b));
        when(salesOrderRepository.findWithDetailsById(1L)).thenReturn(Optional.of(a));
        when(salesOrderRepository.findWithDetailsById(2L)).thenReturn(Optional.of(b));
        when(payosOrderExpireHandler.expireIfNeeded(a)).thenReturn(true);
        when(payosOrderExpireHandler.expireIfNeeded(b)).thenReturn(false);

        HoldExpireService service = newService();

        assertThat(service.run().releasedOrders()).isEqualTo(1);
    }

    @Test
    void run_usesCutoffNowMinusGrace() {
        // Ân hạn đọc từ app_config (không cố định 2 phút): đổi 5 phút → chỉ quét đơn quá giờ giữ hàng 5 phút
        when(appConfigService.paymentGraceMinutes()).thenReturn(5);
        when(salesOrderRepository.findExpiredHolds(any())).thenReturn(List.of());
        HoldExpireService service = new HoldExpireService(
                salesOrderRepository, payosOrderExpireHandler, appConfigService, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.run().releasedOrders()).isZero();

        org.mockito.ArgumentCaptor<Instant> cutoff = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(salesOrderRepository).findExpiredHolds(cutoff.capture());
        assertThat(cutoff.getValue()).isEqualTo(NOW.minusSeconds(300));
    }

    private static void setId(Object entity, long id) {
        try {
            var idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
