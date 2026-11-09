package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HoldExpireServiceTest {

    @Mock
    private OrderAllocationService orderAllocationService;

    @Test
    void run_delegatesToAllocator() {
        when(orderAllocationService.releaseExpiredHolds()).thenReturn(2);
        HoldExpireService service = new HoldExpireService(orderAllocationService);

        assertThat(service.run().releasedOrders()).isEqualTo(2);
        verify(orderAllocationService).releaseExpiredHolds();
    }
}
