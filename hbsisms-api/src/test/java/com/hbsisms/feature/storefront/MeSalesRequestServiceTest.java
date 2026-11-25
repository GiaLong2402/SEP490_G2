package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.Quote;
import com.hbsisms.feature.sales.QuoteLine;
import com.hbsisms.feature.sales.QuoteRepository;
import com.hbsisms.feature.sales.QuoteStatus;
import com.hbsisms.feature.sales.RequestStatus;
import com.hbsisms.feature.sales.SaleChannel;
import com.hbsisms.feature.sales.SalesOrderService;
import com.hbsisms.feature.sales.SalesRequest;
import com.hbsisms.feature.sales.SalesRequestRepository;
import com.hbsisms.feature.sales.SellUnit;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.storefront.dto.MeSalesRequestRejectRequest;
import com.hbsisms.feature.storefront.dto.MeSalesRequestResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeSalesRequestServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-25T07:00:00Z"), ZoneOffset.UTC);

    @Mock
    private SalesRequestRepository salesRequestRepository;
    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private SalesOrderService salesOrderService;
    @Mock
    private BusinessCodeService businessCodeService;

    private MeSalesRequestService service;

    @BeforeEach
    void setUp() {
        service = new MeSalesRequestService(
                salesRequestRepository, quoteRepository, salesOrderService, businessCodeService, CLOCK);
    }

    @Test
    void getMine_includesQuotePrices() {
        Customer customer = customer(1L);
        SalesRequest row = request(10L, "YC-1", customer, RequestStatus.QUOTING);
        Quote quote = quote(50L, "BG-1", QuoteStatus.SENT, row);
        when(salesRequestRepository.findByCodeAndCustomer_Id("YC-1", 1L)).thenReturn(Optional.of(row));
        when(quoteRepository.findFirstByRequest_IdOrderByIdDesc(10L)).thenReturn(Optional.of(quote));
        when(quoteRepository.findWithDetailsById(50L)).thenReturn(Optional.of(quote));

        MeSalesRequestResponse response = service.getMine(customer, "YC-1");

        assertThat(response.quote()).isNotNull();
        assertThat(response.quote().code()).isEqualTo("BG-1");
        assertThat(response.quote().total()).isEqualByComparingTo("450000");
        assertThat(response.quote().lines()).hasSize(1);
        assertThat(response.quote().lines().get(0).unitPrice()).isEqualByComparingTo("150000");
    }

    @Test
    void accept_convertsSentQuoteToOrder() {
        Customer customer = customer(1L);
        SalesRequest row = request(10L, "YC-1", customer, RequestStatus.QUOTING);
        Quote quote = quote(50L, "BG-1", QuoteStatus.SENT, row);
        when(salesRequestRepository.findByCodeAndCustomer_Id("YC-1", 1L)).thenReturn(Optional.of(row));
        when(quoteRepository.findFirstByRequest_IdOrderByIdDesc(10L)).thenReturn(Optional.of(quote));
        when(quoteRepository.findWithDetailsById(50L)).thenReturn(Optional.of(quote));
        OrderDetailResponse order = mock(OrderDetailResponse.class);
        when(order.code()).thenReturn("SO-100");
        when(salesOrderService.createFromQuote(eq(50L), eq(9L))).thenReturn(order);

        MeSalesRequestResponse response = service.accept(customer, "YC-1", 9L);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.APPROVED);
        assertThat(row.getStatus()).isEqualTo(RequestStatus.ORDERED);
        assertThat(row.getOrderCode()).isEqualTo("SO-100");
        assertThat(response.status()).isEqualTo("ORDERED");
        assertThat(response.orderCode()).isEqualTo("SO-100");
    }

    @Test
    void accept_whenNoSentQuote_returns409() {
        Customer customer = customer(1L);
        SalesRequest row = request(10L, "YC-1", customer, RequestStatus.QUOTING);
        when(salesRequestRepository.findByCodeAndCustomer_Id("YC-1", 1L)).thenReturn(Optional.of(row));
        when(quoteRepository.findFirstByRequest_IdOrderByIdDesc(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(customer, "YC-1", 9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(salesOrderService, never()).createFromQuote(any(), any());
    }

    @Test
    void reject_requiresReasonAndMarksRejected() {
        Customer customer = customer(1L);
        SalesRequest row = request(10L, "YC-1", customer, RequestStatus.QUOTING);
        Quote quote = quote(50L, "BG-1", QuoteStatus.SENT, row);
        when(salesRequestRepository.findByCodeAndCustomer_Id("YC-1", 1L)).thenReturn(Optional.of(row));
        when(quoteRepository.findFirstByRequest_IdOrderByIdDesc(10L)).thenReturn(Optional.of(quote));
        when(quoteRepository.findWithDetailsById(50L)).thenReturn(Optional.of(quote));

        MeSalesRequestResponse response =
                service.reject(customer, "YC-1", new MeSalesRequestRejectRequest("Giá cao quá"));

        assertThat(row.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(row.getRejectReason()).isEqualTo("Giá cao quá");
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(response.rejectReason()).isEqualTo("Giá cao quá");
    }

    @Test
    void reject_blankReason_returns400() {
        Customer customer = customer(1L);
        SalesRequest row = request(10L, "YC-1", customer, RequestStatus.QUOTING);
        when(salesRequestRepository.findByCodeAndCustomer_Id("YC-1", 1L)).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> service.reject(customer, "YC-1", new MeSalesRequestRejectRequest("  ")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static Customer customer(Long id) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", id);
        c.setActive(true);
        c.setName("Nguyễn Văn Mười");
        return c;
    }

    private static SalesRequest request(Long id, String code, Customer customer, RequestStatus status) {
        SalesRequest row = new SalesRequest();
        ReflectionTestUtils.setField(row, "id", id);
        row.setCode(code);
        row.setCustomer(customer);
        row.setChannel(SaleChannel.WEB);
        row.setSummary("Chả mực × 20");
        row.setStatus(status);
        row.setReceivedAt(Instant.parse("2026-11-23T10:00:00Z"));
        row.setFeasible(true);
        row.setFulfillableQty(20);
        row.setShortageQty(0);
        return row;
    }

    private static Quote quote(Long id, String code, QuoteStatus status, SalesRequest request) {
        Quote quote = new Quote();
        ReflectionTestUtils.setField(quote, "id", id);
        quote.setCode(code);
        quote.setStatus(status);
        quote.setRequest(request);
        quote.setCustomer(request.getCustomer());
        quote.setSubtotal(new BigDecimal("450000"));
        quote.setTotal(new BigDecimal("450000"));

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setName("Chả Mực Giã Tay");
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", 2L);
        variant.setSku("CM-05");
        variant.setPackLabel("Khay 0,5 kg");
        variant.setProduct(product);

        QuoteLine line = new QuoteLine();
        line.setVariant(variant);
        line.setUnit(SellUnit.PACK);
        line.setQty(3);
        line.setQtyKhay(3);
        line.setUnitPrice(new BigDecimal("150000"));
        line.setLineTotal(new BigDecimal("450000"));
        line.setPriceBasis(PriceBasis.LIST);
        quote.addLine(line);
        return quote;
    }

}
