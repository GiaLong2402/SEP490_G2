package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddressRepository;
import com.hbsisms.feature.customer.CustomerAddressService;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.payment.CustomerDebtLedgerRepository;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentRepository;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
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
class SalesOrderStockGuardTest {

    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private OrderEventRepository orderEventRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private CustomerAddressRepository customerAddressRepository;
    @Mock
    private CustomerAddressService customerAddressService;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private CustomerDebtLedgerRepository customerDebtLedgerRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private OrderAllocationService orderAllocationService;
    @Mock
    private AuditLogService auditLogService;

    private SalesOrderService service;

    @BeforeEach
    void setUp() {
        service = new SalesOrderService(
                salesOrderRepository,
                orderEventRepository,
                deliveryRepository,
                paymentRepository,
                customerRepository,
                customerAddressRepository,
                customerAddressService,
                productVariantRepository,
                userRepository,
                approvalTaskRepository,
                customerDebtLedgerRepository,
                quoteRepository,
                pricingService,
                auditLogService,
                orderAllocationService,
                Clock.fixed(Instant.parse("2026-11-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void create_rejectsWhenRequestedQtyExceedsAvailable() {
        Customer customer = withId(new Customer(), 1L);
        customer.setActive(true);
        customer.setCustomerType(CustomerType.B2C);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        when(pricingService.resolve(any()))
                .thenReturn(new PricingResultResponse(
                        List.of(new PricedLineResponse(
                                10L,
                                SellUnit.PACK,
                                8,
                                8,
                                new BigDecimal("100000.00"),
                                PriceBasis.LIST,
                                new BigDecimal("800000.00"),
                                5L,
                                3L)),
                        new BigDecimal("800000.00"),
                        new BigDecimal("800000.00"),
                        false,
                        BigDecimal.ZERO));

        Product product = new Product();
        product.setName("Chả mực");
        ProductVariant variant = withId(new ProductVariant(), 10L);
        variant.setPackLabel("Khay 0,5kg");
        variant.setProduct(product);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        OrderInputRequest request = new OrderInputRequest(
                1L,
                null,
                null,
                OrderType.RETAIL,
                SaleChannel.PHONE,
                PaymentMethod.CASH,
                FulfillmentMethod.PICKUP,
                null,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                List.of(new PricingLineInput(10L, SellUnit.PACK, 8, null)));

        assertThatThrownBy(() -> service.create(request, null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).containsIgnoringCase("không đủ hàng");
                    assertThat(api.getMessage()).contains("chỉ còn 5");
                });

        verify(salesOrderRepository, never()).save(any());
        verify(orderAllocationService, never()).holdForOrder(any(), any());
    }

    private static <T> T withId(T entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
