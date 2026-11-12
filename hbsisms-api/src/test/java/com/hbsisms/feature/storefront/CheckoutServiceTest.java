package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.pricing.PriceResolver;
import com.hbsisms.feature.customer.pricing.ResolvedPrice;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.payment.payos.PayosLinkService;
import com.hbsisms.feature.sales.DeliveryRepository;
import com.hbsisms.feature.sales.FulfillmentMethod;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.OrderEventRepository;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.SalesOrder;
import com.hbsisms.feature.sales.SalesOrderRepository;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.storefront.dto.CheckoutLineRequest;
import com.hbsisms.feature.storefront.dto.CheckoutPaymentResponse;
import com.hbsisms.feature.storefront.dto.CheckoutRequest;
import com.hbsisms.feature.storefront.dto.CheckoutResponse;
import com.hbsisms.feature.storefront.dto.ShortageItem;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private CheckoutIdempotencyRepository idempotencyRepository;
    @Mock
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private PriceResolver priceResolver;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private CartService cartService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AppConfigService appConfigService;
    @Mock
    private PayosLinkService payosLinkService;

    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        checkoutService = new CheckoutService(
                idempotencyRepository,
                customerAddressRepository,
                productVariantRepository,
                stockRepository,
                priceResolver,
                salesOrderRepository,
                orderEventRepository,
                deliveryRepository,
                orderAllocationService,
                paymentRepository,
                salesOrderService,
                cartService,
                userRepository,
                appConfigService,
                payosLinkService,
                CLOCK);
    }

    @Test
    void checkout_missingIdempotencyKey_rejects() {
        assertThatThrownBy(() -> checkoutService.checkout(1L, customer(3L), "  ", pickupRequest(20L, 1), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void checkout_insufficientStock_conflictsWithShortages() {
        ProductVariant variant = sellableVariant(20L, 10L);
        when(idempotencyRepository.findByUserIdAndIdemKey(1L, "k1")).thenReturn(Optional.empty());
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));
        when(stockRepository.sumAvailableByVariantExcludingExpired(20L, LocalDate.of(2026, 11, 12)))
                .thenReturn(2L);

        assertThatThrownBy(() -> checkoutService.checkout(1L, customer(3L), "k1", pickupRequest(20L, 5), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getShortages()).hasSize(1);
                    assertThat(api.getShortages().get(0)).isEqualTo(new ShortageItem(20L, 5, 2));
                });

        verify(salesOrderRepository, never()).save(any());
        verify(orderAllocationService, never()).holdForOrder(any(), any());
        verify(payosLinkService, never()).createForCheckout(any(), any(), any());
    }

    @Test
    void checkout_sameIdempotencyKey_returnsExistingWithoutCreating() {
        CheckoutIdempotency existing = new CheckoutIdempotency();
        existing.setUserId(1L);
        existing.setIdemKey("idem-1");
        existing.setOrderId(99L);

        OrderDetailResponse detail = orderDetail(99L, "SO-2026-0099");
        Payment payment = payosPayment();
        CheckoutPaymentResponse payBlock =
                new CheckoutPaymentResponse(42L, new BigDecimal("189000"), "https://pay.example/x", null, NOW);

        when(idempotencyRepository.findByUserIdAndIdemKey(1L, "idem-1")).thenReturn(Optional.of(existing));
        when(salesOrderService.getDetail(99L)).thenReturn(detail);
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(99L)).thenReturn(List.of(payment));
        when(payosLinkService.toCheckoutPayment(eq(payment), any())).thenReturn(payBlock);

        CheckoutResponse response =
                checkoutService.checkout(1L, customer(3L), "idem-1", pickupRequest(20L, 1), null);

        assertThat(response.order().id()).isEqualTo(99L);
        assertThat(response.payment().checkoutUrl()).isEqualTo("https://pay.example/x");
        verify(productVariantRepository, never()).findWithProductById(anyLong());
        verify(salesOrderRepository, never()).save(any());
        verify(orderAllocationService, never()).holdForOrder(any(), any());
    }

    @Test
    void checkout_success_holdsStock_createsPayos_andSavesIdempotency() {
        ProductVariant variant = sellableVariant(20L, 10L);
        Customer customer = customer(3L);
        OrderDetailResponse detail = orderDetail(55L, "SO-2026-0001");
        Payment payment = payosPayment();
        CheckoutPaymentResponse payBlock =
                new CheckoutPaymentResponse(7L, new BigDecimal("189000"), "https://pay.example/ok", "qr", NOW);

        when(idempotencyRepository.findByUserIdAndIdemKey(1L, "new-key")).thenReturn(Optional.empty());
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));
        when(stockRepository.sumAvailableByVariantExcludingExpired(20L, LocalDate.of(2026, 11, 12)))
                .thenReturn(10L);
        when(priceResolver.resolve(3L, 20L, 1))
                .thenReturn(new ResolvedPrice(new BigDecimal("189000.00"), PriceBasis.LIST));
        when(appConfigService.paymentHoldMinutes()).thenReturn(30);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        when(salesOrderRepository.findCodesByPrefix("SO-2026-")).thenReturn(List.of());
        when(salesOrderRepository.save(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder order = inv.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 55L);
            }
            return order;
        });
        when(orderAllocationService.holdForOrder(any(SalesOrder.class), eq(1L))).thenReturn(1);
        when(salesOrderService.getDetail(55L)).thenReturn(detail);
        when(paymentRepository.findByOrder_IdOrderByPaidAtAscIdAsc(55L)).thenReturn(List.of(payment));
        when(payosLinkService.toCheckoutPayment(eq(payment), any())).thenReturn(payBlock);

        CheckoutResponse response =
                checkoutService.checkout(1L, customer, "new-key", pickupRequest(20L, 1), null);

        assertThat(response.order().code()).isEqualTo("SO-2026-0001");
        assertThat(response.payment().payosOrderCode()).isEqualTo(7L);

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        verify(orderAllocationService).holdForOrder(orderCaptor.capture(), eq(1L));
        assertThat(orderCaptor.getValue().getId()).isEqualTo(55L);
        verify(payosLinkService).createForCheckout(any(SalesOrder.class), eq(customer), any(Instant.class));
        ArgumentCaptor<CheckoutIdempotency> idemCaptor = ArgumentCaptor.forClass(CheckoutIdempotency.class);
        verify(idempotencyRepository).save(idemCaptor.capture());
        assertThat(idemCaptor.getValue().getIdemKey()).isEqualTo("new-key");
        assertThat(idemCaptor.getValue().getOrderId()).isEqualTo(55L);
    }

    @Test
    void checkout_lockedCustomer_conflicts() {
        Customer locked = customer(3L);
        locked.setActive(false);
        when(idempotencyRepository.findByUserIdAndIdemKey(1L, "k")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkoutService.checkout(1L, locked, "k", pickupRequest(20L, 1), null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void checkout_pastRequestedDate_rejects() {
        when(idempotencyRepository.findByUserIdAndIdemKey(1L, "k")).thenReturn(Optional.empty());
        CheckoutRequest request = new CheckoutRequest(
                FulfillmentMethod.PICKUP,
                null,
                LocalDate.of(2026, 11, 11),
                "08:00-10:00",
                null,
                PaymentMethod.PAYOS,
                List.of(new CheckoutLineRequest(20L, 1)));

        assertThatThrownBy(() -> checkoutService.checkout(1L, customer(3L), "k", request, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static CheckoutRequest pickupRequest(long variantId, int qty) {
        return new CheckoutRequest(
                FulfillmentMethod.PICKUP,
                null,
                LocalDate.of(2026, 11, 13),
                "08:00-10:00",
                null,
                PaymentMethod.PAYOS,
                List.of(new CheckoutLineRequest(variantId, qty)));
    }

    private static Customer customer(long id) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", id);
        customer.setActive(true);
        return customer;
    }

    private static ProductVariant sellableVariant(Long variantId, Long productId) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setPublished(true);
        product.setActive(true);
        product.setName("Tôm sú");

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        variant.setProduct(product);
        variant.setSku("TOM-SU-500");
        variant.setActive(true);
        variant.setListPrice(new BigDecimal("189000.00"));
        return variant;
    }

    private static Payment payosPayment() {
        Payment payment = new Payment();
        payment.setMethod(PaymentMethod.PAYOS);
        payment.setPayosOrderCode(42L);
        payment.setCheckoutUrl("https://pay.example/x");
        payment.setAmount(new BigDecimal("189000"));
        return payment;
    }

    private static OrderDetailResponse orderDetail(long id, String code) {
        return new OrderDetailResponse(
                id,
                code,
                3L,
                null,
                null,
                null,
                PaymentMethod.PAYOS,
                (short) 0,
                null,
                null,
                FulfillmentMethod.PICKUP,
                null,
                null,
                new BigDecimal("189000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("189000"),
                null,
                false,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                NOW.plusSeconds(1800),
                null,
                NOW,
                NOW,
                "KH",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                null,
                null,
                null,
                List.of(),
                true);
    }
}
