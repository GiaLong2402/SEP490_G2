package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.BomLineRequest;
import com.hbsisms.feature.catalog.dto.BomReplaceRequest;
import com.hbsisms.feature.catalog.dto.BomResponse;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import java.math.BigDecimal;
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
class ProductBomServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductBomLineRepository bomLineRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private UnitOfMeasureRepository unitRepository;

    private ProductBomService service;

    @BeforeEach
    void setUp() {
        service = new ProductBomService(productRepository, bomLineRepository, rawMaterialRepository, unitRepository);
    }

    @Test
    void replace_savesRawMaterialLine() {
        Product product = product(1L, "cha-muc", "Chả mực");
        RawMaterial raw = raw(2L, "NL-MUC", "Mực tươi", true);
        UnitOfMeasure kg = unit(3L, "kg", true);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(rawMaterialRepository.findById(2L)).thenReturn(Optional.of(raw));
        when(unitRepository.findById(3L)).thenReturn(Optional.of(kg));
        when(bomLineRepository.findByProduct_IdOrderBySortOrderAscIdAsc(1L)).thenReturn(List.of());

        BomResponse response = service.replace(
                1L,
                new BomReplaceRequest(List.of(
                        new BomLineRequest(2L, null, new BigDecimal("0.85"), 3L, new BigDecimal("5")))));

        ArgumentCaptor<List<ProductBomLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(bomLineRepository).deleteByProduct_Id(1L);
        verify(bomLineRepository).saveAll(captor.capture());
        ProductBomLine saved = captor.getValue().get(0);
        assertThat(saved.getRawMaterial()).isEqualTo(raw);
        assertThat(saved.getQty()).isEqualByComparingTo("0.85");
        assertThat(saved.getWastePercent()).isEqualByComparingTo("5");
        assertThat(response.productId()).isEqualTo(1L);
    }

    @Test
    void replace_rejectsSelfAsComponentProduct() {
        Product product = product(1L, "cha-muc", "Chả mực");
        UnitOfMeasure kg = unit(3L, "kg", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(unitRepository.findById(3L)).thenReturn(Optional.of(kg));

        assertThatThrownBy(() -> service.replace(
                        1L,
                        new BomReplaceRequest(List.of(
                                new BomLineRequest(null, 1L, new BigDecimal("1"), 3L, null)))))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST))
                .hasMessageContaining("chính sản phẩm");
        verify(bomLineRepository, never()).saveAll(anyList());
    }

    @Test
    void replace_rejectsDuplicateRawMaterial() {
        Product product = product(1L, "cha-muc", "Chả mực");
        RawMaterial raw = raw(2L, "NL-MUC", "Mực", true);
        UnitOfMeasure kg = unit(3L, "kg", true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(rawMaterialRepository.findById(2L)).thenReturn(Optional.of(raw));
        when(unitRepository.findById(3L)).thenReturn(Optional.of(kg));

        assertThatThrownBy(() -> service.replace(
                        1L,
                        new BomReplaceRequest(List.of(
                                new BomLineRequest(2L, null, new BigDecimal("1"), 3L, null),
                                new BomLineRequest(2L, null, new BigDecimal("2"), 3L, null)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("trùng");
    }

    @Test
    void replace_rejectsNeitherOrBothComponentKinds() {
        Product product = product(1L, "cha-muc", "Chả mực");
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.replace(
                        1L,
                        new BomReplaceRequest(List.of(
                                new BomLineRequest(null, null, new BigDecimal("1"), 3L, null)))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đúng một");
    }

    private static Product product(Long id, String code, String name) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setCode(code);
        product.setName(name);
        product.setActive(true);
        return product;
    }

    private static RawMaterial raw(Long id, String code, String name, boolean active) {
        RawMaterial raw = new RawMaterial();
        ReflectionTestUtils.setField(raw, "id", id);
        raw.setCode(code);
        raw.setName(name);
        raw.setUnit("kg");
        raw.setActive(active);
        return raw;
    }

    private static UnitOfMeasure unit(Long id, String code, boolean active) {
        UnitOfMeasure unit = new UnitOfMeasure();
        ReflectionTestUtils.setField(unit, "id", id);
        unit.setCode(code);
        unit.setName(code);
        unit.setActive(active);
        return unit;
    }
}
