package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.inventory.dto.RawMaterialRequest;
import com.hbsisms.feature.inventory.dto.RawMaterialResponse;
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
class RawMaterialServiceTest {

    @Mock
    private RawMaterialRepository rawMaterialRepository;

    private RawMaterialService service;

    @BeforeEach
    void setUp() {
        service = new RawMaterialService(rawMaterialRepository);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("NL-TOM", "Tôm", "kg", null, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(rawMaterialRepository, never()).save(any());
    }

    @Test
    void create_startsWithZeroStock() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(inv -> {
            RawMaterial m = inv.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 1L);
            return m;
        });

        RawMaterialResponse response = service.create(request(" NL-TOM ", " Tôm biển ", "kg", new BigDecimal("80"), true));

        assertThat(response.code()).isEqualTo("NL-TOM");
        assertThat(response.name()).isEqualTo("Tôm biển");
        assertThat(response.qtyOnHand()).isEqualByComparingTo("0");
        assertThat(response.reorderLevel()).isEqualByComparingTo("80");
        assertThat(response.isActive()).isTrue();
    }

    @Test
    void update_rejectsUnknownId() {
        when(rawMaterialRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("NL-X", "X", "kg", null, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_filtersByQueryActiveAndLowStock() {
        RawMaterial muc = material(1L, "NL-MUC", "Mực tươi", "kg", "185", "100", true);
        RawMaterial tom = material(2L, "NL-TOM", "Tôm biển", "kg", "50", "80", true);
        RawMaterial bao = material(3L, "NL-BAO", "Bao nilon", "cuộn", "10", "5", false);
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(bao, muc, tom));

        assertThat(service.list("tôm", null, null)).extracting(RawMaterialResponse::code).containsExactly("NL-TOM");
        assertThat(service.list(null, true, null)).extracting(RawMaterialResponse::code).containsExactly("NL-MUC", "NL-TOM");
        assertThat(service.list(null, null, true)).extracting(RawMaterialResponse::code).containsExactly("NL-TOM");
    }

    @Test
    void get_returnsRow() {
        when(rawMaterialRepository.findById(1L)).thenReturn(Optional.of(material(1L, "NL-MUC", "Mực", "kg", "1", null, true)));

        RawMaterialResponse response = service.get(1L);

        assertThat(response.code()).isEqualTo("NL-MUC");
        assertThat(response.name()).isEqualTo("Mực");
    }

    private static RawMaterialRequest request(
            String code, String name, String unit, BigDecimal reorderLevel, boolean active) {
        return new RawMaterialRequest(code, name, unit, reorderLevel, active);
    }

    private static RawMaterial material(
            Long id,
            String code,
            String name,
            String unit,
            String qty,
            String reorder,
            boolean active) {
        RawMaterial m = new RawMaterial();
        ReflectionTestUtils.setField(m, "id", id);
        m.setCode(code);
        m.setName(name);
        m.setUnit(unit);
        m.setQtyOnHand(new BigDecimal(qty));
        m.setReorderLevel(reorder == null ? null : new BigDecimal(reorder));
        m.setActive(active);
        return m;
    }
}
