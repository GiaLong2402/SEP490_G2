package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
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
class DeliveryServiceTest {

    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesOrderLineRepository salesOrderLineRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private DeliveryService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC);
    private AppUser actor;

    @BeforeEach
    void setUp() {
        service = new DeliveryService(
                deliveryRepository,
                salesOrderRepository,
                salesOrderLineRepository,
                orderEventRepository,
                orderAllocationService,
                userRepository,
                auditLogService,
                clock);
        actor = new AppUser();
        setId(actor, 9L);
        actor.setFullName("Shipper");
    }

    @Test
    void update_deliveredWhenCodUnpaid_returns409() {
        SalesOrder order = order(20L, PaymentMethod.COD, PaymentStatus.UNPAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(30L, order, DeliveryState.GOING);
        when(deliveryRepository.findById(30L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.update(30L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null, null), 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("COD");
                });
    }

    @Test
    void update_deliveredWhenCashShopDeliveryUnpaid_returns409() {
        SalesOrder order = order(21L, PaymentMethod.CASH, PaymentStatus.UNPAID, OrderState.TO_SHIP);
        order.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        Delivery delivery = delivery(31L, order, DeliveryState.GOING);
        when(deliveryRepository.findById(31L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.update(31L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null, null), 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("thanh toán đủ");
                });
    }

    @Test
    void update_deliveredWhenCodPaid_succeeds() {
        SalesOrder order = order(22L, PaymentMethod.COD, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(32L, order, DeliveryState.GOING);
        SalesOrderLine line = new SalesOrderLine();
        setId(line, 40L);
        line.setQtyKhay(1);
        when(deliveryRepository.findById(32L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(22L)).thenReturn(List.of(line));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(22L)).thenReturn(List.of(delivery));

        service.update(32L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null, null), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        verify(orderAllocationService).issueOutHeldForOrder(eq(order), eq(9L));
        verify(deliveryRepository).save(delivery);
    }

    private SalesOrder order(long id, PaymentMethod method, PaymentStatus pay, OrderState state) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode("SO-" + id);
        order.setPaymentMethod(method);
        order.setPaymentStatus(pay);
        order.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        order.setState(state);
        order.setAllocationStatus(AllocationStatus.FULL);
        return order;
    }

    private Delivery delivery(long id, SalesOrder order, DeliveryState state) {
        Delivery d = new Delivery();
        setId(d, id);
        d.setCode("GH-" + id);
        d.setOrder(order);
        d.setLegLabel("Đợt 1");
        d.setState(state);
        d.setFulfillmentMethod(FulfillmentMethod.SHOP_DELIVERY);
        d.setItems(List.of(new DeliveryItem(40L, 1)));
        return d;
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
