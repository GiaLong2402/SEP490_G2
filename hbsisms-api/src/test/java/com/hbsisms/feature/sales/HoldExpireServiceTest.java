package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.payment.payos.PayosOrderExpireHandler;
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

    @Test
    void run_processesEachExpiredOrderViaHandler() {
        SalesOrder header = new SalesOrder();
        setId(header, 9L);
        SalesOrder detailed = new SalesOrder();
        setId(detailed, 9L);
        when(salesOrderRepository.findExpiredHolds(NOW)).thenReturn(List.of(header));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(detailed));
        when(payosOrderExpireHandler.expireIfNeeded(detailed)).thenReturn(true);

        HoldExpireService service = new HoldExpireService(
                salesOrderRepository, payosOrderExpireHandler, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.run().releasedOrders()).isEqualTo(1);
        verify(payosOrderExpireHandler).expireIfNeeded(detailed);
    }

    @Test
    void run_countsOnlyResolvedOrders() {
        SalesOrder a = new SalesOrder();
        setId(a, 1L);
        SalesOrder b = new SalesOrder();
        setId(b, 2L);
        when(salesOrderRepository.findExpiredHolds(NOW)).thenReturn(List.of(a, b));
        when(salesOrderRepository.findWithDetailsById(1L)).thenReturn(Optional.of(a));
        when(salesOrderRepository.findWithDetailsById(2L)).thenReturn(Optional.of(b));
        when(payosOrderExpireHandler.expireIfNeeded(a)).thenReturn(true);
        when(payosOrderExpireHandler.expireIfNeeded(b)).thenReturn(false);

        HoldExpireService service = new HoldExpireService(
                salesOrderRepository, payosOrderExpireHandler, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.run().releasedOrders()).isEqualTo(1);
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
