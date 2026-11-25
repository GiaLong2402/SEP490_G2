package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.UnitOfMeasure;
import com.hbsisms.feature.catalog.UnitOfMeasureRepository;
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
    @Mock
    private UnitOfMeasureRepository unitRepository;
    @Mock
    private LocationRepository locationRepository;

    private RawMaterialService service;

    @BeforeEach
    void setUp() {
        service = new RawMaterialService(rawMaterialRepository, unitRepository, locationRepository);
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
        when(locationRepository.findById(COLD_ZONE_ID)).thenReturn(Optional.of(zone(COLD_ZONE_ID, "LANH", true)));
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

    @Test
    void create_prefersUnitIdAndDenormalizesCode() {
        UnitOfMeasure kg = unit(3L, "kg", "Ki-lô-gam", true);
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);
        when(unitRepository.findById(3L)).thenReturn(Optional.of(kg));
        when(locationRepository.findById(COLD_ZONE_ID)).thenReturn(Optional.of(zone(COLD_ZONE_ID, "LANH", true)));
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(inv -> inv.getArgument(0));

        RawMaterialResponse response =
                service.create(new RawMaterialRequest("NL-TOM", "Tôm", 3L, "legacy", null, true, COLD_ZONE_ID));

        assertThat(response.unitId()).isEqualTo(3L);
        assertThat(response.unit()).isEqualTo("kg");
    }

    @Test
    void create_rejectsInactiveUnit() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);
        when(unitRepository.findById(3L)).thenReturn(Optional.of(unit(3L, "kg", "Ki-lô-gam", false)));

        assertThatThrownBy(() -> service.create(new RawMaterialRequest("NL-TOM", "Tôm", 3L, null, null, true, COLD_ZONE_ID)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(rawMaterialRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingUnitIdAndLegacyUnit() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new RawMaterialRequest("NL-TOM", "Tôm", null, "  ", null, true, COLD_ZONE_ID)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_requiresStorageZone() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);

        assertThatThrownBy(() -> service.create(new RawMaterialRequest("NL-TOM", "Tôm", null, "kg", null, true, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("khu cất");
        verify(rawMaterialRepository, never()).save(any());
    }

    @Test
    void create_rejectsZoneOfFinishedWarehouse() {
        when(rawMaterialRepository.existsByCodeIgnoreCase("NL-TOM")).thenReturn(false);
        when(locationRepository.findById(5L)).thenReturn(Optional.of(zone(5L, "CHA", false)));

        assertThatThrownBy(() -> service.create(new RawMaterialRequest("NL-TOM", "Tôm", null, "kg", null, true, 5L)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("kho nguyên liệu");
        verify(rawMaterialRepository, never()).save(any());
    }

    @Test
    void update_keepsZoneWhenNotSent_andResponseCarriesZone() {
        RawMaterial muc = material(1L, "NL-MUC", "Mực tươi", "kg", "5", null, true);
        muc.setStorageLocation(zone(COLD_ZONE_ID, "LANH", true));
        when(rawMaterialRepository.findById(1L)).thenReturn(Optional.of(muc));
        when(rawMaterialRepository.existsByCodeIgnoreCaseAndIdNot("NL-MUC", 1L)).thenReturn(false);
        when(rawMaterialRepository.save(any(RawMaterial.class))).thenAnswer(inv -> inv.getArgument(0));

        RawMaterialResponse response =
                service.update(1L, new RawMaterialRequest("NL-MUC", "Mực tươi", null, "kg", null, true, null));

        assertThat(response.storageLocationId()).isEqualTo(COLD_ZONE_ID);
        assertThat(response.storageLocationCode()).isEqualTo("LANH");
        assertThat(response.storageLocationName()).isEqualTo("Khu LANH");
    }

    @Test
    void list_filtersByStorageZone() {
        RawMaterial muc = material(1L, "NL-MUC", "Mực tươi", "kg", "5", null, true);
        muc.setStorageLocation(zone(COLD_ZONE_ID, "LANH", true));
        RawMaterial khay = material(2L, "NL-KHAY", "Khay xốp", "cai", "70", null, true);
        khay.setStorageLocation(zone(DRY_ZONE_ID, "KHO", true));
        when(rawMaterialRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(khay, muc));

        assertThat(service.list(null, null, null, COLD_ZONE_ID)).extracting(RawMaterialResponse::code).containsExactly("NL-MUC");
        assertThat(service.list(null, null, null, DRY_ZONE_ID)).extracting(RawMaterialResponse::code).containsExactly("NL-KHAY");
        assertThat(service.list(null, null, null, null)).hasSize(2);
    }

    private static final Long COLD_ZONE_ID = 30L;
    private static final Long DRY_ZONE_ID = 31L;

    private static Location zone(Long id, String code, boolean rawWarehouse) {
        Warehouse warehouse = new Warehouse();
        warehouse.setKind(rawWarehouse ? WarehouseKind.RAW : WarehouseKind.FINISHED);
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", id);
        location.setWarehouse(warehouse);
        location.setCode(code);
        location.setZone("Khu " + code);
        return location;
    }

    private static UnitOfMeasure unit(Long id, String code, String name, boolean active) {
        UnitOfMeasure u = new UnitOfMeasure();
        ReflectionTestUtils.setField(u, "id", id);
        u.setCode(code);
        u.setName(name);
        u.setActive(active);
        return u;
    }

    private static RawMaterialRequest request(            String code, String name, String unit, BigDecimal reorderLevel, boolean active) {
        return new RawMaterialRequest(code, name, null, unit, reorderLevel, active, COLD_ZONE_ID);
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
