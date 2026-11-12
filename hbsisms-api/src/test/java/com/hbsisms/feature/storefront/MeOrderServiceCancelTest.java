package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.payos.PayosClient;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEvent;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.OrderState;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeOrderServiceCancelTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private PayosClient payosClient;

    private MeOrderService service;

    @BeforeEach
    void setUp() {
        service = new MeOrderService(
                salesOrderRepository,
                deliveryRepository,
                paymentRepository,
                orderEventRepository,
                orderAllocationService,
                payosClient);
    }

    @Test
    void cancelMine_pendingPayos_callsCancelLink() {
        Customer customer = customer(3L);
        SalesOrder order = unpaidOrder(9L, "SO-1", customer);
        Payment payment = pendingPayos(order, 42L, "plink-1");

        when(salesOrderRepository.findByCodeAndCustomer_Id("SO-1", 3L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.cancelPaymentRequest("plink-1", "Khách hủy đơn")).thenReturn(true);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(9L)).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(9L)).thenReturn(List.of());

        service.cancelMine(customer, "SO-1");

        verify(payosClient).cancelPaymentRequest("plink-1", "Khách hủy đơn");
        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(payment.getCheckoutUrl()).isNull();
        assertThat(payment.getNote()).contains("đã hủy link PayOS");
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        verify(orderAllocationService).releaseForOrder(eq(order), any(), anyString());
    }

    @Test
    void cancelMine_payosCancelFails_stillCancelsLocally() {
        Customer customer = customer(3L);
        SalesOrder order = unpaidOrder(9L, "SO-1", customer);
        Payment payment = pendingPayos(order, 42L, "plink-1");

        when(salesOrderRepository.findByCodeAndCustomer_Id("SO-1", 3L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(payment));
        when(payosClient.cancelPaymentRequest(anyString(), anyString())).thenReturn(false);
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(9L)).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(9L)).thenReturn(List.of());

        service.cancelMine(customer, "SO-1");

        assertThat(payment.getStatus()).isEqualTo(PayTxnStatus.CANCELLED);
        assertThat(order.getState()).isEqualTo(OrderState.CANCELLED);
    }

    @Test
    void cancelMine_nonPayosPending_doesNotCallPayos() {
        Customer customer = customer(3L);
        SalesOrder order = unpaidOrder(9L, "SO-1", customer);
        Payment cash = new Payment();
        cash.setOrder(order);
        cash.setMethod(PaymentMethod.CASH);
        cash.setStatus(PayTxnStatus.PENDING);

        when(salesOrderRepository.findByCodeAndCustomer_Id("SO-1", 3L)).thenReturn(Optional.of(order));
        when(salesOrderRepository.findWithDetailsById(9L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(9L)).thenReturn(List.of(cash));
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(deliveryRepository.findByOrder_IdOrderByIdAsc(9L)).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(9L)).thenReturn(List.of());

        service.cancelMine(customer, "SO-1");

        verify(payosClient, never()).cancelPaymentRequest(anyString(), anyString());
        assertThat(cash.getStatus()).isEqualTo(PayTxnStatus.PENDING);
    }

    private static Customer customer(long id) {
        Customer customer = new Customer();
        setId(customer, id);
        return customer;
    }

    private static SalesOrder unpaidOrder(long id, String code, Customer customer) {
        SalesOrder order = new SalesOrder();
        setId(order, id);
        order.setCode(code);
        order.setCustomer(customer);
        order.setState(OrderState.PENDING);
        order.setPaymentStatus(PaymentStatus.WAITING);
        return order;
    }

    private static Payment pendingPayos(SalesOrder order, long orderCode, String linkId) {
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setPayosOrderCode(orderCode);
        payment.setCheckoutUrl("https://pay.payos.vn/web/x");
        payment.setRawPayload(new HashMap<>(Map.of("paymentLinkId", linkId)));
        return payment;
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
