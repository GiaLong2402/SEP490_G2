package com.hbsisms.feature.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductBomLineRepository;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.production.dto.ProductionBomProductResponse;
import com.hbsisms.feature.production.dto.ProductionMaterialPreviewResponse;
import java.math.BigDecimal;
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
class ProductionPlanningServiceTest {

    @Mock
    private ProductBomLineRepository bomLineRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private BomExplosionService bomExplosionService;
    @Mock
    private ProductVariantRepository productVariantRepository;

    private ProductionPlanningService service;

    @BeforeEach
    void setUp() {
        service = new ProductionPlanningService(
                bomLineRepository, productRepository, bomExplosionService, productVariantRepository);
    }

    @Test
    void listBomProducts_returnsProductsWithCounts() {
        Product p = product(10L, "TP-001", "Mực Lá", true);
        when(bomLineRepository.findActiveProductsWithRawBom()).thenReturn(List.of(p));
        when(bomLineRepository.countRawLinesByProductId(10L)).thenReturn(2L);

        List<ProductionBomProductResponse> result = service.listBomProducts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).bomLineCount()).isEqualTo(2L);
        assertThat(result.get(0).isProducible()).isTrue();
    }

    @Test
    void previewMaterials_mapsExplosion() {
        Product p = product(10L, "TP-001", "Mực Lá", true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));

        RawMaterial raw = new RawMaterial();
        ReflectionTestUtils.setField(raw, "id", 5L);
        raw.setCode("NL-MUC");
        raw.setName("Mực");
        raw.setUnit("kg");

        ProductionOrderMaterialLine line = new ProductionOrderMaterialLine();
        line.setRawMaterial(raw);
        line.setBomQtyPerUnit(new BigDecimal("0.85"));
        line.setWastePercent(new BigDecimal("5"));
        line.setRequiredQty(new BigDecimal("89.2500"));
        line.setOnHandQty(new BigDecimal("100"));
        line.setSufficient(true);
        line.setLineEstimatedCost(new BigDecimal("500"));

        when(bomExplosionService.explode(eq(10L), any(), any()))
                .thenReturn(new BomExplosionService.ExplosionResult(
                        List.of(line), new BigDecimal("500.00"), List.of(new BigDecimal("12"))));

        ProductionMaterialPreviewResponse preview =
                service.previewMaterials(10L, new BigDecimal("100"));

        assertThat(preview.productCode()).isEqualTo("TP-001");
        assertThat(preview.allMaterialsSufficient()).isTrue();
        assertThat(preview.materials()).hasSize(1);
        assertThat(preview.materials().get(0).onHandQty()).isEqualByComparingTo("100");
        assertThat(preview.materials().get(0).expiredQty()).isEqualByComparingTo("12");
    }

    @Test
    void listBomProducts_listsActiveTraysOneKgFirst() {
        Product p = product(10L, "TP-001", "Chả cá", true);
        when(bomLineRepository.findActiveProductsWithRawBom()).thenReturn(List.of(p));
        when(bomLineRepository.countRawLinesByProductId(10L)).thenReturn(3L);
        ProductVariant half = variant(p, 21L, "CHA-05", 500, true);
        ProductVariant stopped = variant(p, 22L, "CHA-02", 2000, false);
        ProductVariant oneKg = variant(p, 23L, "CHA-10", 1000, true);
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(half, stopped, oneKg));

        List<ProductionBomProductResponse.VariantOption> variants = service.listBomProducts().get(0).variants();

        assertThat(variants).extracting(ProductionBomProductResponse.VariantOption::id).containsExactly(23L, 21L);
        assertThat(variants.get(1).unitWeightG()).isEqualTo(500);
    }

    @Test
    void previewMaterials_noVariant_usesSameDefaultTrayAsCreate() {
        Product p = product(10L, "TP-001", "Mực Lá", true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));
        ProductVariant half = variant(p, 21L, "MUC-05", 500, true);
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(half));
        when(bomExplosionService.explode(10L, BigDecimal.ONE, half))
                .thenReturn(new BomExplosionService.ExplosionResult(List.of(), BigDecimal.ZERO));

        service.previewMaterials(10L, BigDecimal.ONE);

        org.mockito.Mockito.verify(bomExplosionService).explode(10L, BigDecimal.ONE, half);
    }

    @Test
    void previewMaterials_variantOfOtherProduct_returns400() {
        Product p = product(10L, "TP-001", "Mực Lá", true);
        Product other = product(11L, "TP-002", "Chả cá", true);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));
        when(productVariantRepository.findById(31L)).thenReturn(Optional.of(variant(other, 31L, "CHA-10", 1000, true)));

        assertThatThrownBy(() -> service.previewMaterials(10L, BigDecimal.ONE, 31L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void previewMaterials_inactiveProduct_returns400() {
        Product p = product(10L, "TP-001", "Mực Lá", true);
        p.setActive(false);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.previewMaterials(10L, BigDecimal.ONE))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static ProductVariant variant(Product product, Long id, String sku, int grams, boolean active) {
        ProductVariant v = new ProductVariant();
        ReflectionTestUtils.setField(v, "id", id);
        v.setProduct(product);
        v.setSku(sku);
        v.setUnitWeightG(grams);
        v.setActive(active);
        return v;
    }

    private static Product product(Long id, String code, String name, boolean producible) {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", id);
        p.setCode(code);
        p.setName(name);
        p.setActive(true);
        p.setRawMaterial(false);
        p.setProducible(producible);
        return p;
    }
}
