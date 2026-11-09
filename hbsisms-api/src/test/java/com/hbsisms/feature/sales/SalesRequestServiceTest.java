package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestFeasibilityRequest;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestRejectRequest;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class SalesRequestServiceTest {

    @Mock
    private SalesRequestRepository salesRequestRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SalesOrderService salesOrderService;

    private SalesRequestService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new SalesRequestService(
                salesRequestRepository, customerRepository, userRepository, salesOrderService, clock);
    }

    @Test
    void create_persistsNewRequest() {
        when(salesRequestRepository.findCodesByPrefix("YC-2026-")).thenReturn(List.of());
        when(salesRequestRepository.save(any(SalesRequest.class))).thenAnswer(inv -> {
            SalesRequest r = inv.getArgument(0);
            setId(r, 1L);
            return r;
        });

        var response = service.create(
                new SalesRequestInputRequest(null, "Anh A", SaleChannel.PHONE, "Cần 10 khay tôm"), 9L);

        assertThat(response.code()).isEqualTo("YC-2026-0001");
        assertThat(response.status()).isEqualTo(RequestStatus.NEW);
        assertThat(response.customerName()).isEqualTo("Anh A");
    }

    @Test
    void reject_requiresReasonAndSetsRejected() {
        SalesRequest row = openRequest(3L);
        when(salesRequestRepository.findWithDetailsById(3L)).thenReturn(Optional.of(row));

        var response = service.reject(3L, new SalesRequestRejectRequest("Hết hàng lâu"), 1L);

        assertThat(response.status()).isEqualTo(RequestStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("Hết hàng lâu");
        assertThat(response.feasible()).isFalse();
    }

    @Test
    void reject_blankReasonFails() {
        SalesRequest row = openRequest(1L);
        when(salesRequestRepository.findWithDetailsById(1L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.reject(1L, new SalesRequestRejectRequest("  "), 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void feasibility_customerAcceptedCreatesOrder() {
        SalesRequest row = openRequest(4L);
        when(salesRequestRepository.findWithDetailsById(4L)).thenReturn(Optional.of(row));
        OrderInputRequest orderBody = new OrderInputRequest(
                1L,
                OrderType.B2B,
                SaleChannel.PHONE,
                com.hbsisms.feature.payment.PaymentMethod.CASH,
                FulfillmentMethod.PICKUP,
                null,
                null,
                null,
                null,
                null,
                List.of());
        // NotEmpty on lines would fail validation at controller; here we stub create.
        OrderDetailResponse stubOrder = org.mockito.Mockito.mock(OrderDetailResponse.class);
        when(salesOrderService.createFromRequest(any(), eq(1L))).thenReturn(stubOrder);

        var result = service.recordFeasibility(
                4L,
                new SalesRequestFeasibilityRequest(true, 10, 0, null, null, true, orderBody),
                1L);

        assertThat(result.request().status()).isEqualTo(RequestStatus.ORDERED);
        assertThat(result.request().feasible()).isTrue();
        assertThat(result.order()).isSameAs(stubOrder);
        verify(salesOrderService).createFromRequest(any(), eq(1L));
    }

    @Test
    void feasibility_acceptedWithoutOrderFails() {
        SalesRequest row = openRequest(5L);
        when(salesRequestRepository.findWithDetailsById(5L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.recordFeasibility(
                        5L,
                        new SalesRequestFeasibilityRequest(true, 5, 0, null, null, true, null),
                        1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private SalesRequest openRequest(long id) {
        SalesRequest row = new SalesRequest();
        setId(row, id);
        row.setCode("YC-2026-000" + id);
        row.setChannel(SaleChannel.PHONE);
        row.setSummary("test");
        row.setStatus(RequestStatus.NEW);
        row.setReceivedAt(Instant.now(clock));
        return row;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
