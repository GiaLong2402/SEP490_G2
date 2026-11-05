package com.hbsisms.feature.customer.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.catalog.VolumeTier;
import com.hbsisms.feature.catalog.VolumeTierRepository;
import com.hbsisms.feature.sales.PriceBasis;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PriceResolverTest {

    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private CustomerPriceRepository customerPriceRepository;
    @Mock
    private VolumeTierRepository volumeTierRepository;

    private PriceResolver priceResolver;

    @BeforeEach
    void setUp() {
        priceResolver = new PriceResolver(productVariantRepository, customerPriceRepository, volumeTierRepository);
    }

    @Test
    void prefersApprovedCustomerPriceWhenInEffect() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(customerPriceRepository.findEffectiveSpecial(eq(2L), eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.of(special("78000")));

        ResolvedPrice result = priceResolver.resolve(2L, 10L, 1);

        assertThat(result.unitPrice()).isEqualByComparingTo("78000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.CUSTOMER);
        verify(volumeTierRepository, never())
                .findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(any(), any(Integer.class));
    }

    @Test
    void usesHighestMatchingVolumeTierWhenNoSpecial() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(customerPriceRepository.findEffectiveSpecial(eq(2L), eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(volumeTierRepository.findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(10L, 20))
                .thenReturn(List.of(tier(20, "80000"), tier(5, "82000")));

        ResolvedPrice result = priceResolver.resolve(2L, 10L, 20);

        assertThat(result.unitPrice()).isEqualByComparingTo("80000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.TIER);
    }

    @Test
    void fallsBackToListPrice() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(volumeTierRepository.findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(10L, 1))
                .thenReturn(List.of());

        ResolvedPrice result = priceResolver.resolve(null, 10L, 1);

        assertThat(result.unitPrice()).isEqualByComparingTo("90000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.LIST);
        verify(customerPriceRepository, never()).findEffectiveSpecial(any(), any(), any());
    }

    @Test
    void rejectsUnknownVariant() {
        when(productVariantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> priceResolver.resolve(1L, 99L, 1))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsNonPositiveQty() {
        assertThatThrownBy(() -> priceResolver.resolve(1L, 10L, 0))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsNullVariantId() {
        assertThatThrownBy(() -> priceResolver.resolve(1L, null, 1))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void customerWithoutSpecial_fallsBackToHighestTier() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(customerPriceRepository.findEffectiveSpecial(eq(2L), eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(volumeTierRepository.findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(10L, 15))
                .thenReturn(List.of(tier(10, "81000"), tier(5, "85000")));

        ResolvedPrice result = priceResolver.resolve(2L, 10L, 15);

        assertThat(result.unitPrice()).isEqualByComparingTo("81000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.TIER);
    }

    @Test
    void customerWithoutSpecialOrTier_fallsBackToList() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(customerPriceRepository.findEffectiveSpecial(eq(2L), eq(10L), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(volumeTierRepository.findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(10L, 2))
                .thenReturn(List.of());

        ResolvedPrice result = priceResolver.resolve(2L, 10L, 2);

        assertThat(result.unitPrice()).isEqualByComparingTo("90000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.LIST);
    }

    @Test
    void rejectsNegativeQty() {
        assertThatThrownBy(() -> priceResolver.resolve(1L, 10L, -3))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void guestWithMatchingTier_usesTierWithoutLookingUpSpecial() {
        when(productVariantRepository.findById(10L)).thenReturn(Optional.of(variant(10L, "90000")));
        when(volumeTierRepository.findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(10L, 25))
                .thenReturn(List.of(tier(20, "79000")));

        ResolvedPrice result = priceResolver.resolve(null, 10L, 25);

        assertThat(result.unitPrice()).isEqualByComparingTo("79000");
        assertThat(result.priceBasis()).isEqualTo(PriceBasis.TIER);
        verify(customerPriceRepository, never()).findEffectiveSpecial(any(), any(), any());
    }

    private static ProductVariant variant(Long id, String listPrice) {
        ProductVariant v = new ProductVariant();
        ReflectionTestUtils.setField(v, "id", id);
        v.setListPrice(new BigDecimal(listPrice));
        return v;
    }

    private static CustomerPrice special(String unitPrice) {
        CustomerPrice p = new CustomerPrice();
        p.setUnitPrice(new BigDecimal(unitPrice));
        p.setApprovedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return p;
    }

    private static VolumeTier tier(int minQty, String unitPrice) {
        VolumeTier t = new VolumeTier();
        t.setMinQtyKhay(minQty);
        t.setUnitPrice(new BigDecimal(unitPrice));
        return t;
    }
}
