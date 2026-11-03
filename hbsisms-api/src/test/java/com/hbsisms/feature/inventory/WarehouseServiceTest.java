package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.inventory.dto.LocationRequest;
import com.hbsisms.feature.inventory.dto.WarehouseRequest;
import com.hbsisms.feature.inventory.dto.WarehouseResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private StockRepository stockRepository;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(warehouseRepository, locationRepository, stockRepository);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-1")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.create(warehouseRequest("KHO-1", "Kho 1", true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void create_trimsAndPersists() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-1")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            w.setCode(w.getCode());
            return withId(w, 10L);
        });
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(10L)).thenReturn(List.of());

        WarehouseResponse response = warehouseService.create(warehouseRequest(" KHO-1 ", " Kho chính ", true));

        assertThat(response.code()).isEqualTo("KHO-1");
        assertThat(response.name()).isEqualTo("Kho chính");
        assertThat(response.isActive()).isTrue();
        assertThat(response.totalQty()).isZero();
    }

    @Test
    void update_blocksDeactivateWhenStockRemains() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByCodeIgnoreCaseAndIdNot("KHO-1", 1L)).thenReturn(false);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> warehouseService.update(1L, warehouseRequest("KHO-1", "Kho 1", false)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void deleteLocation_blocksWhenStockExists() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", true), 1L);
        Location location = withLocationId(newLocation(warehouse, "A-01"), 7L);
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(stockRepository.existsByLocation_Id(7L)).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.deleteLocation(7L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(locationRepository, never()).delete(any());
    }

    @Test
    void addLocation_rejectsNonPositiveCapacity() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> warehouseService.addLocation(1L, new LocationRequest("A-01", null, 0, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void list_includesUsedQtyAndCanDelete() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", true), 1L);
        Location empty = withLocationId(newLocation(warehouse, "A-01"), 2L);
        Location used = withLocationId(newLocation(warehouse, "B-01"), 3L);
        when(warehouseRepository.findAll(Sort.by(Sort.Direction.ASC, "code"))).thenReturn(List.of(warehouse));
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of(empty, used));
        when(stockRepository.sumQtyByLocationId(2L)).thenReturn(0L);
        when(stockRepository.sumQtyByLocationId(3L)).thenReturn(12L);
        when(stockRepository.existsByLocation_Id(2L)).thenReturn(false);
        when(stockRepository.existsByLocation_Id(3L)).thenReturn(true);

        List<WarehouseResponse> list = warehouseService.list();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).totalQty()).isEqualTo(12L);
        assertThat(list.get(0).locations().get(0).canDelete()).isTrue();
        assertThat(list.get(0).locations().get(1).canDelete()).isFalse();
        assertThat(list.get(0).locations().get(1).usedQty()).isEqualTo(12L);
    }

    private static WarehouseRequest warehouseRequest(String code, String name, boolean active) {
        return new WarehouseRequest(code, name, null, active);
    }

    private static Warehouse newWarehouse(String code, String name, boolean active) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setActive(active);
        return warehouse;
    }

    private static Location newLocation(Warehouse warehouse, String code) {
        Location location = new Location();
        location.setWarehouse(warehouse);
        location.setCode(code);
        return location;
    }

    private static Warehouse withId(Warehouse warehouse, Long id) {
        try {
            var field = Warehouse.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(warehouse, id);
            return warehouse;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Location withLocationId(Location location, Long id) {
        try {
            var field = Location.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(location, id);
            return location;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
