package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.TierInput;
import com.hbsisms.feature.catalog.dto.VariantRequest;
import com.hbsisms.feature.inventory.StockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class VariantServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private VolumeTierRepository volumeTierRepository;
    @Mock
    private StockRepository stockRepository;

    private VariantService variantService;

    @BeforeEach
    void setUp() {
        variantService = new VariantService(
                productRepository, productVariantRepository, volumeTierRepository, stockRepository);
    }

    // --- create ---

    @Test
    void create_notFoundWhenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.create(99L, validReq()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_persistsVariantAndReplacesTiers() {
        Product product = withId(new Product(), 10L);
        product.setCode("P-TOM");
        product.setName("Tôm");
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(inv -> withId((ProductVariant) inv.getArgument(0), 50L));

        variantService.create(
                10L,
                new VariantRequest(
                        " SKU-1 ",
                        " Khay 0,5kg ",
                        500,
                        new BigDecimal("100000"),
                        12,
                        " 123 ",
                        true,
                        List.of(
                                new TierInput(20, new BigDecimal("90000")),
                                new TierInput(10, new BigDecimal("95000")))));

        ArgumentCaptor<ProductVariant> variantCaptor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(variantCaptor.capture());
        ProductVariant saved = variantCaptor.getValue();
        assertThat(saved.getSku()).isEqualTo("SKU-1");
        assertThat(saved.getPackLabel()).isEqualTo("Khay 0,5kg");
        assertThat(saved.getUnitWeightG()).isEqualTo(500);
        assertThat(saved.getListPrice()).isEqualByComparingTo("100000");
        assertThat(saved.getPackPerCase()).isEqualTo((short) 12);
        assertThat(saved.getBarcode()).isEqualTo("123");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getProduct()).isEqualTo(product);

        InOrder order = Mockito.inOrder(volumeTierRepository);
        order.verify(volumeTierRepository).deleteByVariant_Id(50L);
        order.verify(volumeTierRepository).flush();

        ArgumentCaptor<VolumeTier> tierCaptor = ArgumentCaptor.forClass(VolumeTier.class);
        verify(volumeTierRepository, times(2)).save(tierCaptor.capture());
        assertThat(tierCaptor.getAllValues())
                .extracting(VolumeTier::getMinQtyKhay)
                .containsExactly(10, 20);
        assertThat(tierCaptor.getAllValues().get(0).getUnitPrice()).isEqualByComparingTo("95000");
        assertThat(tierCaptor.getAllValues().get(1).getUnitPrice()).isEqualByComparingTo("90000");
    }

    @Test
    void create_rejectsBlankSku() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));

        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("  ", "Khay", null, bd("100"), 1, null, true, null)));
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_rejectsBlankPackLabel() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));

        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "  ", null, bd("100"), 1, null, true, null)));
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_rejectsInvalidListPrice() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));

        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, null, 1, null, true, null)));
        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, BigDecimal.ZERO, 1, null, true, null)));
        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, bd("-1"), 1, null, true, null)));
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_rejectsInvalidPackPerCase() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));

        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, bd("100"), null, null, true, null)));
        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, bd("100"), 0, null, true, null)));
        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1", "Khay", null, bd("100"), Short.MAX_VALUE + 1, null, true, null)));
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateSku() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> variantService.create(10L, validReq()))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("SKU-1");
                });
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_rejectsNegativeWeight() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);

        assertBadRequest(() -> variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", -1, bd("100"), 1, null, true, null)));
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void create_allowsNullWeightAndEmptyTiers() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(inv -> withId((ProductVariant) inv.getArgument(0), 50L));

        variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, bd("100"), 1, "  ", true, null));

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        assertThat(captor.getValue().getUnitWeightG()).isNull();
        assertThat(captor.getValue().getBarcode()).isNull();
        verify(volumeTierRepository).deleteByVariant_Id(50L);
        verify(volumeTierRepository).flush();
        verify(volumeTierRepository, never()).save(any());
    }

    // --- update ---

    @Test
    void update_notFoundWhenVariantMissing() {
        when(productVariantRepository.findWithProductById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> variantService.update(99L, validReq()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void update_persistsAndReplacesTiers() {
        ProductVariant existing = withId(new ProductVariant(), 50L);
        existing.setProduct(withId(new Product(), 10L));
        existing.setSku("SKU-OLD");
        existing.setPackLabel("Cũ");
        existing.setListPrice(bd("50"));
        existing.setPackPerCase((short) 1);
        existing.setActive(true);

        when(productVariantRepository.findWithProductById(50L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.existsBySkuIgnoreCaseAndIdNot("SKU-1", 50L)).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        variantService.update(
                50L,
                new VariantRequest(
                        "SKU-1",
                        "Khay mới",
                        200,
                        bd("120"),
                        6,
                        null,
                        false,
                        List.of(new TierInput(5, bd("100")))));

        ArgumentCaptor<ProductVariant> captor = ArgumentCaptor.forClass(ProductVariant.class);
        verify(productVariantRepository).save(captor.capture());
        assertThat(captor.getValue().getSku()).isEqualTo("SKU-1");
        assertThat(captor.getValue().getPackLabel()).isEqualTo("Khay mới");
        assertThat(captor.getValue().isActive()).isFalse();
        verify(volumeTierRepository).deleteByVariant_Id(50L);
        verify(volumeTierRepository).flush();
        verify(volumeTierRepository).save(any(VolumeTier.class));
    }

    @Test
    void update_rejectsDuplicateSkuOnOtherId() {
        ProductVariant existing = withId(new ProductVariant(), 50L);
        existing.setProduct(withId(new Product(), 10L));
        when(productVariantRepository.findWithProductById(50L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.existsBySkuIgnoreCaseAndIdNot("SKU-TAKEN", 50L)).thenReturn(true);

        assertThatThrownBy(() -> variantService.update(
                        50L, new VariantRequest("SKU-TAKEN", "Khay", null, bd("100"), 1, null, true, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(productVariantRepository, never()).save(any());
    }

    @Test
    void update_allowsSameSkuAsSelf() {
        ProductVariant existing = withId(new ProductVariant(), 50L);
        existing.setProduct(withId(new Product(), 10L));
        existing.setSku("SKU-1");
        when(productVariantRepository.findWithProductById(50L)).thenReturn(Optional.of(existing));
        when(productVariantRepository.existsBySkuIgnoreCaseAndIdNot("SKU-1", 50L)).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(inv -> inv.getArgument(0));

        variantService.update(50L, validReq());

        verify(productVariantRepository).save(any(ProductVariant.class));
    }

    // --- tiers via create ---

    @Test
    void replaceTiers_rejectsNonPositiveMinQty() {
        stubCreateReady();

        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1", "Khay", null, bd("100"), 1, null, true, List.of(new TierInput(0, bd("90"))))));
        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1", "Khay", null, bd("100"), 1, null, true, List.of(new TierInput(null, bd("90"))))));
    }

    @Test
    void replaceTiers_rejectsDuplicateMinQty() {
        stubCreateReady();

        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1",
                        "Khay",
                        null,
                        bd("100"),
                        1,
                        null,
                        true,
                        List.of(new TierInput(10, bd("90")), new TierInput(10, bd("80"))))));
    }

    @Test
    void replaceTiers_rejectsInvalidUnitPrice() {
        stubCreateReady();

        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1", "Khay", null, bd("100"), 1, null, true, List.of(new TierInput(10, null)))));
        assertBadRequest(() -> variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1",
                        "Khay",
                        null,
                        bd("100"),
                        1,
                        null,
                        true,
                        List.of(new TierInput(10, BigDecimal.ZERO)))));
    }

    @Test
    void replaceTiers_rejectsPriceAboveListPrice() {
        stubCreateReady();

        assertThatThrownBy(() -> variantService.create(
                        10L,
                        new VariantRequest(
                                "SKU-1",
                                "Khay",
                                null,
                                bd("100"),
                                1,
                                null,
                                true,
                                List.of(new TierInput(10, bd("101"))))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("không được lớn hơn giá niêm yết");
                });
    }

    @Test
    void replaceTiers_rejectsIncreasingPricesAcrossTiers() {
        stubCreateReady();

        assertThatThrownBy(() -> variantService.create(
                        10L,
                        new VariantRequest(
                                "SKU-1",
                                "Khay",
                                null,
                                bd("100"),
                                1,
                                null,
                                true,
                                List.of(new TierInput(10, bd("90")), new TierInput(20, bd("95"))))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("nhỏ hơn hoặc bằng bậc trước");
                });
    }

    @Test
    void replaceTiers_allowsEqualConsecutivePrices() {
        stubCreateReady();

        variantService.create(
                10L,
                new VariantRequest(
                        "SKU-1",
                        "Khay",
                        null,
                        bd("100"),
                        1,
                        null,
                        true,
                        List.of(new TierInput(10, bd("90")), new TierInput(20, bd("90")))));

        verify(volumeTierRepository, times(2)).save(any(VolumeTier.class));
    }

    @Test
    void replaceTiers_emptyListOnlyDeletes() {
        stubCreateReady();

        variantService.create(
                10L, new VariantRequest("SKU-1", "Khay", null, bd("100"), 1, null, true, List.of()));

        verify(volumeTierRepository).deleteByVariant_Id(50L);
        verify(volumeTierRepository).flush();
        verify(volumeTierRepository, never()).save(any());
    }

    private void stubCreateReady() {
        when(productRepository.findById(10L)).thenReturn(Optional.of(withId(new Product(), 10L)));
        when(productVariantRepository.existsBySkuIgnoreCase("SKU-1")).thenReturn(false);
        when(productVariantRepository.save(any(ProductVariant.class)))
                .thenAnswer(inv -> withId((ProductVariant) inv.getArgument(0), 50L));
    }

    private static VariantRequest validReq() {
        return new VariantRequest("SKU-1", "Khay 0,5kg", null, bd("100"), 1, null, true, null);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static Product withId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
            return product;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static ProductVariant withId(ProductVariant variant, Long id) {
        try {
            var field = ProductVariant.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(variant, id);
            return variant;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
