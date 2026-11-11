package com.hbsisms.feature.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.pricing.PricingService;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.sales.dto.QuoteDetailResponse;
import com.hbsisms.feature.sales.dto.QuoteInputRequest;
import com.hbsisms.feature.sales.dto.QuoteStatusRequest;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
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

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private SalesOrderRepository salesOrderRepository;
    @Mock
    private SalesRequestRepository salesRequestRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private AuditLogService auditLogService;

    private QuoteService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-11T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new QuoteService(
                quoteRepository,
                salesOrderRepository,
                salesRequestRepository,
                customerRepository,
                productVariantRepository,
                userRepository,
                pricingService,
                salesOrderService,
                auditLogService,
                clock);
    }

    @Test
    void create_persistsDraftAndMarksRequestQuoting() {
        Customer customer = withId(new Customer(), 1L);
        customer.setActive(true);
        customer.setName("Anh A");
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));

        SalesRequest request = withId(new SalesRequest(), 7L);
        request.setCode("YC-2026-0007");
        request.setStatus(RequestStatus.NEW);
        request.setCustomer(customer);
        when(salesRequestRepository.findById(7L)).thenReturn(Optional.of(request));

        when(pricingService.resolve(any()))
                .thenReturn(new PricingResultResponse(
                        List.of(new PricedLineResponse(
                                10L,
                                SellUnit.PACK,
                                2,
                                2,
                                new BigDecimal("100000.00"),
                                PriceBasis.LIST,
                                new BigDecimal("200000.00"),
                                20L)),
                        new BigDecimal("200000.00"),
                        new BigDecimal("200000.00"),
                        false,
                        BigDecimal.ZERO));

        Product product = new Product();
        product.setName("Chả mực");
        ProductVariant variant = withId(new ProductVariant(), 10L);
        variant.setSku("SKU-10");
        variant.setPackLabel("Khay 0,5kg");
        variant.setProduct(product);
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant));

        when(quoteRepository.findCodesByPrefix("BG-2026-")).thenReturn(List.of());
        when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> {
            Quote q = inv.getArgument(0);
            return withId(q, 99L);
        });
        when(quoteRepository.findWithDetailsById(99L)).thenAnswer(inv -> {
            Quote q = new Quote();
            withId(q, 99L);
            q.setCode("BG-2026-0001");
            q.setCustomer(customer);
            q.setRequest(request);
            q.setStatus(QuoteStatus.DRAFT);
            q.setSubtotal(new BigDecimal("200000.00"));
            q.setTotal(new BigDecimal("200000.00"));
            q.setValidUntil(LocalDate.of(2026, 11, 20));
            QuoteLine line = new QuoteLine();
            line.setVariant(variant);
            line.setUnit(SellUnit.PACK);
            line.setQty(2);
            line.setQtyKhay(2);
            line.setUnitPrice(new BigDecimal("100000.00"));
            line.setLineTotal(new BigDecimal("200000.00"));
            line.setPriceBasis(PriceBasis.LIST);
            q.addLine(line);
            return Optional.of(q);
        });
        when(salesOrderRepository.findFirstBySourceQuote_Id(99L)).thenReturn(Optional.empty());

        QuoteDetailResponse response = service.create(
                new QuoteInputRequest(
                        1L,
                        7L,
                        LocalDate.of(2026, 11, 20),
                        "Ghi chú",
                        List.of(new PricingLineInput(10L, SellUnit.PACK, 2, null))),
                null);

        assertThat(response.code()).isEqualTo("BG-2026-0001");
        assertThat(response.status()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(response.requestId()).isEqualTo(7L);
        assertThat(response.requestCode()).isEqualTo("YC-2026-0007");
        assertThat(response.lines()).hasSize(1);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.QUOTING);

        ArgumentCaptor<Quote> saved = ArgumentCaptor.forClass(Quote.class);
        verify(quoteRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("BG-2026-0001");
        assertThat(saved.getValue().getLines()).hasSize(1);
        verify(auditLogService).record(isNull(), eq("create"), eq("quote"), eq(99L), any());
    }

    @Test
    void setStatus_draftToSent() {
        Quote quote = withId(new Quote(), 5L);
        quote.setStatus(QuoteStatus.DRAFT);
        when(quoteRepository.findWithHeaderById(5L)).thenReturn(Optional.of(quote));

        service.setStatus(5L, new QuoteStatusRequest(QuoteStatus.SENT), 1L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.SENT);
        verify(auditLogService).record(eq(1L), eq("update"), eq("quote"), eq(5L), any());
    }

    @Test
    void setStatus_sentToApprovedSetsApprover() {
        Quote quote = withId(new Quote(), 5L);
        quote.setStatus(QuoteStatus.SENT);
        quote.setValidUntil(LocalDate.of(2026, 12, 1));
        when(quoteRepository.findWithHeaderById(5L)).thenReturn(Optional.of(quote));
        AppUser approver = withId(new AppUser(), 3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(approver));

        service.setStatus(5L, new QuoteStatusRequest(QuoteStatus.APPROVED), 3L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(quote.getApprovedBy()).isEqualTo(approver);
    }

    @Test
    void setStatus_invalidTransitionFails() {
        Quote quote = withId(new Quote(), 5L);
        quote.setStatus(QuoteStatus.DRAFT);
        when(quoteRepository.findWithHeaderById(5L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.setStatus(5L, new QuoteStatusRequest(QuoteStatus.APPROVED), 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void get_expiresSentPastValidUntil() {
        Customer customer = withId(new Customer(), 1L);
        customer.setName("Anh A");
        Quote quote = withId(new Quote(), 8L);
        quote.setCode("BG-2026-0008");
        quote.setCustomer(customer);
        quote.setStatus(QuoteStatus.SENT);
        quote.setValidUntil(LocalDate.of(2026, 11, 1));
        quote.setSubtotal(BigDecimal.TEN);
        quote.setTotal(BigDecimal.TEN);
        when(quoteRepository.findWithDetailsById(8L)).thenReturn(Optional.of(quote));
        when(salesOrderRepository.findFirstBySourceQuote_Id(8L)).thenReturn(Optional.empty());

        QuoteDetailResponse response = service.get(8L);

        assertThat(response.status()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
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
