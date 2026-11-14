package com.hbsisms.feature.payment.payos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.PayTxnStatus;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.dto.PayosLinkResponse;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.system.AppConfigService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PayosLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final Instant HOLD_EXPIRES = Instant.parse("2026-11-11T00:15:00Z");

    @Mock
    private PayosClient payosClient;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private AppConfigService appConfigService;

    private PayosProperties properties;
    private PayosLinkService service;

    @BeforeEach
    void setUp() {
        properties = new PayosProperties(
                "sandbox",
                "client",
                "api",
                "checksum",
                "https://api-merchant.payos.vn",
                "http://localhost:5173/thanh-toan/ket-qua",
                "http://localhost:5173/thanh-toan/ket-qua");
        service = new PayosLinkService(
                properties,
                payosClient,
                paymentRepository,
                salesOrderService,
                appConfigService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createForCheckout_callsPayosOnceAndPersistsPending() {
        // Link PayOS hết hạn = giờ giữ hàng + 2 phút ân hạn
        when(appConfigService.paymentGraceMinutes()).thenReturn(2);
        SalesOrder order = orderWithTotal("SO-2026-0001", new BigDecimal("150000"));
        Customer customer = new Customer();
        when(paymentRepository.maxPayosOrderCode()).thenReturn(130000L);
        when(payosClient.createPaymentRequest(eq(130001L), eq(150000), anyString(), eq(HOLD_EXPIRES.plusSeconds(120).getEpochSecond())))
                .thenReturn(new PayosPaymentLinkResult(
                        130001L,
                        150000,
                        "https://pay.payos.vn/web/abc",
                        "000201QR",
                        "link-id",
                        "PENDING"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment saved = service.createForCheckout(order, customer, HOLD_EXPIRES);

        assertThat(saved.getStatus()).isEqualTo(PayTxnStatus.PENDING);
        assertThat(saved.getPaidAt()).isNull();
        assertThat(saved.getCheckoutUrl()).isEqualTo("https://pay.payos.vn/web/abc");
        assertThat(saved.getPayosOrderCode()).isEqualTo(130001L);
        assertThat(saved.getRawPayload()).containsEntry("qrCode", "000201QR");
        // SO-2026-0001 → SO20260001 (10) → first 9 = SO2026000
        verify(payosClient).createPaymentRequest(130001L, 150000, "SO2026000", HOLD_EXPIRES.plusSeconds(120).getEpochSecond());
    }

    @Test
    void getActiveLink_reusesWithoutCallingPayos() {
        Payment payment = pendingOwnedPayment(42L, 9L, HOLD_EXPIRES, "https://pay.payos.vn/web/same");
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));
        when(salesOrderService.getDetail(9L)).thenReturn(dummyOrderDetail(9L));

        PayosLinkResponse first = service.getActiveLink(42L, 7L);
        PayosLinkResponse second = service.getActiveLink(42L, 7L);

        assertThat(first.checkoutUrl()).isEqualTo("https://pay.payos.vn/web/same");
        assertThat(second.checkoutUrl()).isEqualTo(first.checkoutUrl());
        verify(payosClient, never()).createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void getActiveLink_expired_returns409() {
        Payment payment = pendingOwnedPayment(42L, 9L, NOW.minusSeconds(1), "https://pay.payos.vn/web/old");
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> service.getActiveLink(42L, 7L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(payosClient, never()).createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void createForCheckout_whenDisabled_returns503() {
        properties = new PayosProperties("off", "", "", "", "https://api-merchant.payos.vn", "", "");
        service = new PayosLinkService(
                properties,
                payosClient,
                paymentRepository,
                salesOrderService,
                appConfigService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() ->
                        service.createForCheckout(orderWithTotal("SO-1", BigDecimal.TEN), new Customer(), HOLD_EXPIRES))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void createForCheckout_invalidAmount_rejectedWithoutCallingPayos() {
        // Tổng tiền 0 / âm / lẻ đồng / không có → 400, không gọi PayOS
        for (BigDecimal total : new BigDecimal[] {BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1000.5"), null}) {
            assertThatThrownBy(() -> service.createForCheckout(orderWithTotal("SO-1", total), new Customer(), HOLD_EXPIRES))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verify(payosClient, never()).createPaymentRequest(anyLong(), anyInt(), anyString(), anyLong());
    }

    @Test
    void getActiveLink_notPendingOrNoUrlOrForeign_rejected() {
        // Payment đã thanh toán / hủy → 409
        Payment paid = pendingOwnedPayment(42L, 9L, HOLD_EXPIRES, "https://pay.payos.vn/web/x");
        paid.setStatus(PayTxnStatus.SUCCESS);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(42L)).thenReturn(Optional.of(paid));
        assertStatus(() -> service.getActiveLink(42L, 7L), HttpStatus.CONFLICT);

        // Link đã bị khóa (mất checkoutUrl) → 409
        Payment noUrl = pendingOwnedPayment(43L, 9L, HOLD_EXPIRES, null);
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(43L)).thenReturn(Optional.of(noUrl));
        assertStatus(() -> service.getActiveLink(43L, 7L), HttpStatus.CONFLICT);

        // Khách khác → 404 (không lộ đơn của người khác)
        Payment foreign = pendingOwnedPayment(44L, 9L, HOLD_EXPIRES, "https://pay.payos.vn/web/y");
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(44L)).thenReturn(Optional.of(foreign));
        assertStatus(() -> service.getActiveLink(44L, 99L), HttpStatus.NOT_FOUND);

        // Không tìm thấy mã → 404
        when(paymentRepository.findWithOrderCustomerByPayosOrderCode(45L)).thenReturn(Optional.empty());
        assertStatus(() -> service.getActiveLink(45L, 7L), HttpStatus.NOT_FOUND);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(status);
    }

    @Test
    void shortDescription_truncatesToNine() {
        assertThat(PayosLinkService.shortDescription("SO-2026-0001")).isEqualTo("SO2026000");
        assertThat(PayosLinkService.shortDescription("ABCDEFGHIJK")).isEqualTo("ABCDEFGHI");
    }

    private static SalesOrder orderWithTotal(String code, BigDecimal total) {
        SalesOrder order = new SalesOrder();
        order.setCode(code);
        order.setTotal(total);
        return order;
    }

    private static Payment pendingOwnedPayment(long payosCode, long orderId, Instant holdExpires, String url) {
        AppUser user = new AppUser();
        // id via reflection-free path: AppUser may not allow setId — use stub subclass if needed
        try {
            var idField = AppUser.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 7L);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }

        Customer customer = new Customer();
        customer.setAccountUser(user);

        SalesOrder order = new SalesOrder();
        try {
            var idField = SalesOrder.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, orderId);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        order.setHoldExpiresAt(holdExpires);

        Payment payment = new Payment();
        payment.setPayosOrderCode(payosCode);
        payment.setStatus(PayTxnStatus.PENDING);
        payment.setAmount(new BigDecimal("10000"));
        payment.setCheckoutUrl(url);
        payment.setCustomer(customer);
        payment.setOrder(order);
        payment.setRawPayload(Map.of("qrCode", "QR"));
        return payment;
    }

    private static OrderDetailResponse dummyOrderDetail(long orderId) {
        return org.mockito.Mockito.mock(OrderDetailResponse.class);
    }
}
