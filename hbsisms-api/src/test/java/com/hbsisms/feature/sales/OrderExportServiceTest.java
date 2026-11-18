package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PaymentRepository;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class OrderExportServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DeliveryRepository deliveryRepository;

    @InjectMocks
    private OrderExportService orderExportService;

    @Test
    void summarizeDelivery_prefersFailedWhenOpen() {
        assertThat(OrderExportService.summarizeDelivery(EnumSet.of(DeliveryState.FAILED)))
                .isEqualTo("Giao thất bại");
        assertThat(OrderExportService.summarizeDelivery(
                        EnumSet.of(DeliveryState.FAILED, DeliveryState.GOING)))
                .isEqualTo("Đang giao");
    }

    @Test
    void export_rejectsWhenOverMaxRows() {
        when(salesOrderRepository.count(any(Specification.class))).thenReturn((long) OrderExportService.MAX_ROWS + 1);

        assertThatThrownBy(() -> orderExportService.export(
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(paymentRepository, deliveryRepository);
    }

    @Test
    void export_writesXlsxForEmptyFilter() {
        when(salesOrderRepository.count(any(Specification.class))).thenReturn(0L);
        when(salesOrderRepository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of());

        byte[] bytes = orderExportService.export(
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // XLSX = ZIP (PK..)
        assertThat(bytes.length).isGreaterThan(100);
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');
    }
}
