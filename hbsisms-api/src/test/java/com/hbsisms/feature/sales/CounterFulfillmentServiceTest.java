package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CounterFulfillmentServiceTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private CounterFulfillmentService service;

    @BeforeEach
    void setUp() {
        service = new CounterFulfillmentService(
                salesOrderRepository,
                orderEventRepository,
                orderAllocationService,
                salesOrderService,
                userRepository,
                auditLogService);
    }

    @Test
    void completeAtCounter_issuesOutAndCompletes() {
        SalesOrder order = paidOrder(9L, OrderState.CONFIRMED);
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(orderAllocationService.issueOutHeldForOrder(order, 1L)).thenReturn(5);
        OrderDetailResponse detail = org.mockito.Mockito.mock(OrderDetailResponse.class);
        when(salesOrderService.getDetail(9L)).thenReturn(detail);

        OrderDetailResponse result = service.completeAtCounter(9L, 1L);

        assertThat(result).isSameAs(detail);
        assertThat(order.getState()).isEqualTo(OrderState.COMPLETED);
        verify(orderAllocationService).issueOutHeldForOrder(order, 1L);
        verify(salesOrderRepository).save(order);
        verify(orderEventRepository).save(any(OrderEvent.class));
    }

    @Test
    void completeAtCounter_secondCallIsIdempotent() {
        SalesOrder order = paidOrder(9L, OrderState.COMPLETED);
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        OrderDetailResponse detail = org.mockito.Mockito.mock(OrderDetailResponse.class);
        when(salesOrderService.getDetail(9L)).thenReturn(detail);

        service.completeAtCounter(9L, 1L);
        service.completeAtCounter(9L, 1L);

        verify(orderAllocationService, never()).issueOutHeldForOrder(any(), anyLong());
        verify(salesOrderService, times(2)).getDetail(9L);
    }

    @Test
    void completeAtCounter_rejectsUnpaid() {
        SalesOrder order = paidOrder(9L, OrderState.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.completeAtCounter(9L, 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private SalesOrder paidOrder(long id, OrderState state) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode("SO-" + id);
        order.setState(state);
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setOrderType(OrderType.RETAIL);
        order.setChannel(SaleChannel.COUNTER);
        order.setAllocationStatus(AllocationStatus.FULL);
        order.setSubtotal(BigDecimal.TEN);
        order.setDiscountTotal(BigDecimal.ZERO);
        order.setShippingFee(BigDecimal.ZERO);
        order.setTotal(BigDecimal.TEN);
        order.setPlacedAt(Instant.parse("2026-11-09T10:00:00Z"));
        return order;
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
