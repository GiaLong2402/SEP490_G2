package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RawWarehouseSeederTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private RawMaterialRepository rawMaterialRepository;

    private RawWarehouseSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new RawWarehouseSeeder(warehouseRepository, locationRepository, rawMaterialRepository);
    }

    @Test
    void firstRun_marksKhoNlRaw_createsTwoZones_andAssignsByUnit() {
        Warehouse khoNl = warehouse(8L, "KHO-NL", WarehouseKind.FINISHED);
        when(warehouseRepository.findAll()).thenReturn(List.of(khoNl));
        when(warehouseRepository.findByCodeIgnoreCase("KHO-NL")).thenReturn(Optional.of(khoNl));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));
        List<Location> saved = new ArrayList<>();
        when(locationRepository.save(any(Location.class))).thenAnswer(inv -> {
            Location l = inv.getArgument(0);
            ReflectionTestUtils.setField(l, "id", 100L + saved.size());
            saved.add(l);
            return l;
        });
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(8L)).thenReturn(List.of()).thenAnswer(inv -> saved);
        RawMaterial muc = material("NL-MUC", "kg");
        RawMaterial khay = material("NL-KHAY", "cai");
        when(rawMaterialRepository.findByStorageLocationIsNull()).thenReturn(List.of(muc, khay));

        seeder.run(null);

        assertThat(khoNl.getKind()).isEqualTo(WarehouseKind.RAW);
        assertThat(saved).extracting(Location::getCode).containsExactly("LANH", "KHO");
        assertThat(saved).extracting(Location::getZone).containsExactly("Khu lạnh", "Khu khô");
        assertThat(muc.getStorageLocation().getCode()).isEqualTo("LANH");
        assertThat(khay.getStorageLocation().getCode()).isEqualTo("KHO");
        verify(rawMaterialRepository).saveAll(List.of(muc, khay));
    }

    @Test
    void laterRun_withZonesAndAssignedMaterials_changesNothing() {
        Warehouse raw = warehouse(8L, "KHO-NL", WarehouseKind.RAW);
        Location cold = new Location();
        cold.setCode("LANH");
        when(warehouseRepository.findAll()).thenReturn(List.of(raw));
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(8L)).thenReturn(List.of(cold));
        when(rawMaterialRepository.findByStorageLocationIsNull()).thenReturn(List.of());

        seeder.run(null);

        verify(warehouseRepository, never()).save(any());
        verify(locationRepository, never()).save(any());
        verify(rawMaterialRepository, never()).saveAll(any());
    }

    @Test
    void freshDb_withoutKhoNl_createsRawWarehouse() {
        when(warehouseRepository.findAll()).thenReturn(List.of());
        when(warehouseRepository.findByCodeIgnoreCase("KHO-NL")).thenReturn(Optional.empty());
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "id", 9L);
            return w;
        });
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(9L)).thenReturn(List.of());
        when(rawMaterialRepository.findByStorageLocationIsNull()).thenReturn(List.of());

        seeder.run(null);

        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("KHO-NL");
        assertThat(captor.getValue().isRaw()).isTrue();
        verify(locationRepository, times(2)).save(any(Location.class));
    }

    private static Warehouse warehouse(Long id, String code, WarehouseKind kind) {
        Warehouse w = new Warehouse();
        ReflectionTestUtils.setField(w, "id", id);
        w.setCode(code);
        w.setName(code);
        w.setCapacity(500);
        w.setKind(kind);
        return w;
    }

    private static RawMaterial material(String code, String unit) {
        RawMaterial m = new RawMaterial();
        m.setCode(code);
        m.setName(code);
        m.setUnit(unit);
        return m;
    }
}
