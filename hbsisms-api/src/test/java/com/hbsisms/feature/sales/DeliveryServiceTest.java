package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private BusinessCodeService businessCodeService;
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
                customerAddressRepository,
                businessCodeService,
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
        when(deliveryRepository.findByIdForUpdate(30L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.update(30L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L))
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
        when(deliveryRepository.findByIdForUpdate(31L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.update(31L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L))
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
        when(deliveryRepository.findByIdForUpdate(32L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(22L)).thenReturn(List.of(line));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(22L)).thenReturn(List.of(delivery));

        service.update(32L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        verify(orderAllocationService).issueOutHeldForOrder(eq(order), eq(9L));
        verify(deliveryRepository).save(delivery);
    }

    // ---- Inter 3 dòng 21: bắt đầu giao (không gán người giao — cách giao chỉ là nhãn) ----

    @Test
    void start_shopDelivery_withoutAssignee_goesGoingAndRecordsStartedAt() {
        SalesOrder order = order(50L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(60L, order, DeliveryState.READY);
        when(deliveryRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        service.update(60L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.GOING);
        assertThat(delivery.getStartedAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(delivery.getDeliveredAt()).isNull();
        assertThat(order.getState()).isEqualTo(OrderState.TO_SHIP);
        assertThat(savedEvent().getDescription()).isEqualTo("Bắt đầu giao Đợt 1 · Shop tự giao");
        verify(auditLogService).record(eq(9L), eq("update"), eq("delivery"), eq(60L), any());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void start_partnerDelivery_eventShowsPartnerLabel() {
        SalesOrder order = order(51L, PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(61L, order, DeliveryState.READY);
        delivery.setFulfillmentMethod(FulfillmentMethod.PARTNER_DELIVERY);
        when(deliveryRepository.findByIdForUpdate(61L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        service.update(61L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.GOING);
        assertThat(savedEvent().getDescription()).isEqualTo("Bắt đầu giao Đợt 1 · Đối tác giao");
    }

    @Test
    void start_pickup_hasNoGoingStep_returns409() {
        SalesOrder order = order(52L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(62L, order, DeliveryState.READY);
        delivery.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        when(deliveryRepository.findByIdForUpdate(62L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertConflict(() -> service.update(62L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L),
                "Không thể chuyển");
        assertThat(delivery.getState()).isEqualTo(DeliveryState.READY);
        assertThat(delivery.getStartedAt()).isNull();
    }

    @Test
    void start_orderNotReadyToShip_returns409() {
        SalesOrder order = order(53L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.PREPARING);
        Delivery delivery = delivery(63L, order, DeliveryState.READY);
        when(deliveryRepository.findByIdForUpdate(63L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertConflict(() -> service.update(63L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L),
                "Chờ giao");
        assertThat(delivery.getState()).isEqualTo(DeliveryState.READY);
    }

    @Test
    void start_alreadyGoing_returns409_keepsStartedAt() {
        SalesOrder order = order(54L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(64L, order, DeliveryState.GOING);
        Instant firstStart = Instant.parse("2026-11-15T08:00:00Z");
        delivery.setStartedAt(firstStart);
        when(deliveryRepository.findByIdForUpdate(64L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        assertConflict(() -> service.update(64L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L),
                "Không thể chuyển");
        assertThat(delivery.getStartedAt()).isEqualTo(firstStart);
    }

    @Test
    void start_failedDelivery_returns409() {
        SalesOrder order = order(55L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(65L, order, DeliveryState.FAILED);
        when(deliveryRepository.findByIdForUpdate(65L)).thenReturn(Optional.of(delivery));

        assertConflict(() -> service.update(65L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L),
                "đã thất bại");
    }

    @Test
    void update_deliveredDelivery_returns409() {
        SalesOrder order = order(56L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.DELIVERED);
        Delivery delivery = delivery(66L, order, DeliveryState.DELIVERED);
        when(deliveryRepository.findByIdForUpdate(66L)).thenReturn(Optional.of(delivery));

        assertConflict(() -> service.update(66L, new DeliveryUpdateRequest(null, "ghi chú"), 9L), "hoàn tất");
    }

    @Test
    void update_noStateNoNote_returns400_withoutLoading() {
        assertThatThrownBy(() -> service.update(67L, new DeliveryUpdateRequest(null, "   "), 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(deliveryRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void update_unknownDelivery_returns404() {
        when(deliveryRepository.findByIdForUpdate(68L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(68L, new DeliveryUpdateRequest(DeliveryState.GOING, null), 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void update_noteOnly_onFailedDelivery_savesNoteWithoutStateChange() {
        SalesOrder order = order(57L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(69L, order, DeliveryState.FAILED);
        when(deliveryRepository.findByIdForUpdate(69L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));

        service.update(69L, new DeliveryUpdateRequest(null, "  Khách hẹn chiều mai  "), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.FAILED);
        assertThat(delivery.getNote()).isEqualTo("Khách hẹn chiều mai");
        verify(orderEventRepository, never()).save(any());
        verify(deliveryRepository).save(delivery);
    }

    @Test
    void methodLabel_matchesInternalWebTags() {
        assertThat(DeliveryService.methodLabel(FulfillmentMethod.SHOP_DELIVERY)).isEqualTo("Shop tự giao");
        assertThat(DeliveryService.methodLabel(FulfillmentMethod.PICKUP)).isEqualTo("Khách đến lấy");
        assertThat(DeliveryService.methodLabel(FulfillmentMethod.PARTNER_DELIVERY)).isEqualTo("Đối tác giao");
    }

    // ---- Inter 3 dòng 23 (sửa lỗi): đợt giao checkout web không có danh sách hàng = giao trọn đơn ----

    @Test
    void delivered_webDeliveryWithoutItems_marksOrderDeliveredAndIssuesStock() {
        SalesOrder order = order(70L, PaymentMethod.PAYOS, PaymentStatus.PAID, OrderState.TO_SHIP);
        order.setChannel(SaleChannel.WEB);
        Delivery delivery = delivery(80L, order, DeliveryState.GOING);
        delivery.setItems(List.of());
        when(deliveryRepository.findByIdForUpdate(80L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(70L)).thenReturn(List.of(line(71L, 3), line(72L, 2)));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(70L)).thenReturn(List.of(delivery));

        service.update(80L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(delivery.getDeliveredAt()).isEqualTo(Instant.parse("2026-11-15T10:00:00Z"));
        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        verify(orderAllocationService).issueOutHeldForOrder(eq(order), eq(9L));
        assertThat(savedEventDescriptions()).contains("Đã giao đủ hàng");
    }

    @Test
    void delivered_webPickupWithoutItems_customerReceived_marksOrderDelivered() {
        SalesOrder order = order(73L, PaymentMethod.PAYOS, PaymentStatus.PAID, OrderState.TO_SHIP);
        order.setChannel(SaleChannel.WEB);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        Delivery delivery = delivery(81L, order, DeliveryState.READY);
        delivery.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        delivery.setItems(List.of());
        when(deliveryRepository.findByIdForUpdate(81L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(73L)).thenReturn(List.of(line(74L, 1)));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(73L)).thenReturn(List.of(delivery));

        service.update(81L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L);

        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        verify(orderAllocationService).issueOutHeldForOrder(eq(order), eq(9L));
        assertThat(savedEventDescriptions()).contains("Khách đã nhận Đợt 1", "Đã giao đủ hàng");
    }

    @Test
    void delivered_webRedeliveryAfterFailed_withoutItems_marksOrderDelivered() {
        SalesOrder order = order(75L, PaymentMethod.PAYOS, PaymentStatus.PAID, OrderState.TO_SHIP);
        order.setChannel(SaleChannel.WEB);
        Delivery failed = delivery(82L, order, DeliveryState.FAILED);
        failed.setItems(List.of());
        Delivery retry = delivery(83L, order, DeliveryState.GOING);
        retry.setItems(List.of());
        when(deliveryRepository.findByIdForUpdate(83L)).thenReturn(Optional.of(retry));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(75L)).thenReturn(List.of(line(76L, 4)));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(75L)).thenReturn(List.of(failed, retry));

        service.update(83L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L);

        assertThat(order.getState()).isEqualTo(OrderState.DELIVERED);
        verify(orderAllocationService).issueOutHeldForOrder(eq(order), eq(9L));
    }

    @Test
    void delivered_itemizedLessThanOrdered_marksOrderPartial_withoutIssuingStock() {
        SalesOrder order = order(77L, PaymentMethod.CASH, PaymentStatus.PAID, OrderState.TO_SHIP);
        Delivery delivery = delivery(84L, order, DeliveryState.GOING);
        delivery.setItems(List.of(new DeliveryItem(78L, 1)));
        when(deliveryRepository.findByIdForUpdate(84L)).thenReturn(Optional.of(delivery));
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(salesOrderLineRepository.findByOrder_IdOrderByIdAsc(77L)).thenReturn(List.of(line(78L, 3)));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(77L)).thenReturn(List.of(delivery));

        service.update(84L, new DeliveryUpdateRequest(DeliveryState.DELIVERED, null), 9L);

        assertThat(order.getState()).isEqualTo(OrderState.PARTIAL);
        verify(orderAllocationService, never()).issueOutHeldForOrder(any(), any());
    }

    private static SalesOrderLine line(long id, int qtyKhay) {
        SalesOrderLine line = new SalesOrderLine();
        setId(line, id);
        line.setQtyKhay(qtyKhay);
        return line;
    }

    private List<String> savedEventDescriptions() {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository, atLeastOnce()).save(captor.capture());
        return captor.getAllValues().stream().map(OrderEvent::getDescription).toList();
    }

    private OrderEvent savedEvent() {
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        return captor.getValue();
    }

    private static void assertConflict(ThrowingCallable call, String messagePart) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains(messagePart);
                });
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
