package com.hbsisms.feature.customer.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.SellUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PriceResolver priceResolver;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private StockRepository stockRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(
                priceResolver, productVariantRepository, customerRepository, stockRepository);
    }

    @Test
    void resolve_enoughStock_shortfallZero() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, 10)));
        when(priceResolver.resolve(2L, 10L, 3))
                .thenReturn(new ResolvedPrice(new BigDecimal("90000.00"), PriceBasis.LIST));
        when(stockRepository.sumAvailableByVariantExcludingExpired(eq(10L), any(LocalDate.class)))
                .thenReturn(10L);

        PricingResultResponse result = pricingService.resolve(request(2L, 3));

        PricedLineResponse line = result.lines().get(0);
        assertThat(line.unitPrice()).isEqualByComparingTo("90000.00");
        assertThat(line.priceBasis()).isEqualTo(PriceBasis.LIST);
        assertThat(line.lineTotal()).isEqualByComparingTo("270000.00");
        assertThat(line.available()).isEqualTo(10L);
        assertThat(line.shortfall()).isZero();
    }

    @Test
    void resolve_insufficientStock_returnsShortfall() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, 10)));
        when(priceResolver.resolve(2L, 10L, 8))
                .thenReturn(new ResolvedPrice(new BigDecimal("90000.00"), PriceBasis.LIST));
        when(stockRepository.sumAvailableByVariantExcludingExpired(eq(10L), any(LocalDate.class)))
                .thenReturn(3L);

        PricingResultResponse result = pricingService.resolve(request(2L, 8));

        PricedLineResponse line = result.lines().get(0);
        assertThat(line.qtyKhay()).isEqualTo(8);
        assertThat(line.available()).isEqualTo(3L);
        assertThat(line.shortfall()).isEqualTo(5L);
        assertThat(line.unitPrice()).isEqualByComparingTo("90000.00");
        assertThat(line.priceBasis()).isEqualTo(PriceBasis.LIST);
        assertThat(line.lineTotal()).isEqualByComparingTo("720000.00");
    }

    private static PricingRequest request(Long customerId, int qty) {
        return new PricingRequest(
                customerId,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(new PricingLineInput(10L, SellUnit.PACK, qty, null)));
    }

    private static ProductVariant variant(Long id, int packPerCase) {
        ProductVariant v = new ProductVariant();
        ReflectionTestUtils.setField(v, "id", id);
        v.setPackPerCase((short) packPerCase);
        v.setListPrice(new BigDecimal("90000.00"));
        return v;
    }
}
