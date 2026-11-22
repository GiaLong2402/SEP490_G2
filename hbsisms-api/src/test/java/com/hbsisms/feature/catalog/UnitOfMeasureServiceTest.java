package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.UnitOfMeasureRequest;
import com.hbsisms.feature.catalog.dto.UnitOfMeasureResponse;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UnitOfMeasureServiceTest {

    @Mock
    private UnitOfMeasureRepository unitRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;
    @Mock
    private ProductBomLineRepository bomLineRepository;

    private UnitOfMeasureService service;

    @BeforeEach
    void setUp() {
        service = new UnitOfMeasureService(unitRepository, productRepository, rawMaterialRepository, bomLineRepository);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(unitRepository.existsByCodeIgnoreCase("kg")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new UnitOfMeasureRequest("kg", "Ki-lô-gam", true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(unitRepository, never()).save(any());
    }

    @Test
    void create_trimsAndSaves() {
        when(unitRepository.existsByCodeIgnoreCase("kg")).thenReturn(false);
        when(unitRepository.save(any(UnitOfMeasure.class))).thenAnswer(inv -> inv.getArgument(0));

        UnitOfMeasureResponse response = service.create(new UnitOfMeasureRequest(" kg ", " Ki-lô-gam ", true));

        assertThat(response.code()).isEqualTo("kg");
        assertThat(response.name()).isEqualTo("Ki-lô-gam");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void update_blocksDeactivateWhenUsedByProduct() {
        UnitOfMeasure unit = unit(1L, "kg", true);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.existsByCodeIgnoreCaseAndIdNot("kg", 1L)).thenReturn(false);
        when(productRepository.existsByUnit_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new UnitOfMeasureRequest("kg", "Ki-lô-gam", false)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(unit.isActive()).isTrue();
    }

    @Test
    void update_blocksDeactivateWhenUsedByRawMaterial() {
        UnitOfMeasure unit = unit(1L, "kg", true);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.existsByCodeIgnoreCaseAndIdNot("kg", 1L)).thenReturn(false);
        when(productRepository.existsByUnit_Id(1L)).thenReturn(false);
        when(rawMaterialRepository.existsByUnitRef_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new UnitOfMeasureRequest("kg", "Ki-lô-gam", false)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void update_deactivatesWhenUnused() {
        UnitOfMeasure unit = unit(1L, "kg", true);
        when(unitRepository.findById(1L)).thenReturn(Optional.of(unit));
        when(unitRepository.existsByCodeIgnoreCaseAndIdNot("kg", 1L)).thenReturn(false);
        when(productRepository.existsByUnit_Id(1L)).thenReturn(false);
        when(rawMaterialRepository.existsByUnitRef_Id(1L)).thenReturn(false);
        when(bomLineRepository.existsByUnit_Id(1L)).thenReturn(false);
        when(unitRepository.save(any(UnitOfMeasure.class))).thenAnswer(inv -> inv.getArgument(0));

        UnitOfMeasureResponse response = service.update(1L, new UnitOfMeasureRequest("kg", "Ki-lô-gam", false));

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void update_unknownIdIsNotFound() {
        when(unitRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(9L, new UnitOfMeasureRequest("kg", "Ki-lô-gam", true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static UnitOfMeasure unit(Long id, String code, boolean active) {
        UnitOfMeasure u = new UnitOfMeasure();
        ReflectionTestUtils.setField(u, "id", id);
        u.setCode(code);
        u.setName("Ki-lô-gam");
        u.setActive(active);
        return u;
    }
}
