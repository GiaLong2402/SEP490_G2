package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.payment.PayDirection;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.system.AuditLog;
import com.hbsisms.feature.system.AuditLogRepository;
import com.hbsisms.feature.user.AppUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderDetailAssemblerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderEventRepository orderEventRepository;

    @Mock
    private SalesReturnRepository salesReturnRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private OrderDetailAssembler assembler;

    @Test
    void assemble_includesPendingPaymentsReturnsAndAuditTrail() {
        SalesOrder order = new SalesOrder();
        setId(order, 7L);
        order.setCode("SO-2026-0007");
        order.setOrderType(OrderType.RETAIL);
        order.setChannel(SaleChannel.PHONE);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setState(OrderState.CONFIRMED);
        order.setSubtotal(new BigDecimal("100000.00"));
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setShippingFee(BigDecimal.ZERO.setScale(2));
        order.setTotal(new BigDecimal("100000.00"));
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO.setScale(2));
        order.setPlacedAt(Instant.parse("2026-11-18T01:00:00Z"));

        Payment pending = new Payment();
        setId(pending, 1L);
        pending.setOrder(order);
        pending.setMethod(PaymentMethod.PAYOS);
        pending.setDirection(PayDirection.IN);
        pending.setAmount(new BigDecimal("100000.00"));
        pending.setStatus(PayTxnStatus.PENDING);
        pending.setGateway("payos");

        Payment success = new Payment();
        setId(success, 2L);
        success.setOrder(order);
        success.setMethod(PaymentMethod.CASH);
        success.setDirection(PayDirection.IN);
        success.setAmount(new BigDecimal("50000.00"));
        success.setStatus(PayTxnStatus.SUCCESS);

        SalesReturn ret = new SalesReturn();
        setId(ret, 3L);
        ret.setCode("TH-0001");
        ret.setOrder(order);
        ret.setAmount(new BigDecimal("10000.00"));
        ret.setStatus(ReturnStatus.PENDING);
        ret.setCreatedAt(Instant.parse("2026-11-18T02:00:00Z"));

        AuditLog log = new AuditLog();
        setId(log, 4L);
        log.setAction("update");
        AppUser user = new AppUser();
        setId(user, 9L);
        user.setFullName("Chủ cửa hàng");
        log.setUser(user);
        log.setCreatedAt(Instant.parse("2026-11-18T03:00:00Z"));

        when(deliveryRepository.findByOrder_IdOrderByIdAsc(7L)).thenReturn(List.of());
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(7L)).thenReturn(List.of(pending, success));
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of());
        when(salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(7L)).thenReturn(List.of(ret));
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        eq(SalesOrderService.REF_TYPE), eq(7L)))
                .thenReturn(List.of(log));

        OrderDetailResponse detail = assembler.assemble(order);

        assertThat(detail.payments()).hasSize(2);
        assertThat(detail.payments().stream().map(p -> p.status())).contains(PayTxnStatus.PENDING, PayTxnStatus.SUCCESS);
        assertThat(detail.paidAmount()).isEqualByComparingTo("50000.00");
        assertThat(detail.returns()).hasSize(1);
        assertThat(detail.returns().get(0).code()).isEqualTo("TH-0001");
        assertThat(detail.auditTrail()).hasSize(1);
        assertThat(detail.auditTrail().get(0).actorName()).isEqualTo("Chủ cửa hàng");
        assertThat(detail.deliveries()).isEmpty();
        assertThat(detail.events()).isEmpty();
    }

    @Test
    void assemble_emptyCollectionsWhenNoRelatedData() {
        SalesOrder order = new SalesOrder();
        setId(order, 8L);
        order.setCode("SO-2026-0008");
        order.setOrderType(OrderType.RETAIL);
        order.setChannel(SaleChannel.PHONE);
        order.setPaymentMethod(PaymentMethod.CASH);
        order.setPaymentStatus(PaymentStatus.UNPAID);
        order.setAllocationStatus(AllocationStatus.NONE);
        order.setFulfillmentMethod(FulfillmentMethod.PICKUP);
        order.setState(OrderState.CONFIRMED);
        order.setSubtotal(BigDecimal.ZERO.setScale(2));
        order.setDiscountTotal(BigDecimal.ZERO.setScale(2));
        order.setShippingFee(BigDecimal.ZERO.setScale(2));
        order.setTotal(BigDecimal.ZERO.setScale(2));
        order.setOverLimit(false);
        order.setOverLimitAmount(BigDecimal.ZERO.setScale(2));
        order.setPlacedAt(Instant.parse("2026-11-18T01:00:00Z"));

        when(deliveryRepository.findByOrder_IdOrderByIdAsc(anyLong())).thenReturn(List.of());
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(anyLong())).thenReturn(List.of());
        when(orderEventRepository.findByOrder_IdOrderByCreatedAtAscIdAsc(anyLong())).thenReturn(List.of());
        when(salesReturnRepository.findByOrder_IdOrderByCreatedAtDesc(anyLong())).thenReturn(List.of());
        when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(eq(SalesOrderService.REF_TYPE), anyLong()))
                .thenReturn(List.of());

        OrderDetailResponse detail = assembler.assemble(order);

        assertThat(detail.returns()).isEmpty();
        assertThat(detail.auditTrail()).isEmpty();
        assertThat(detail.payments()).isEmpty();
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
