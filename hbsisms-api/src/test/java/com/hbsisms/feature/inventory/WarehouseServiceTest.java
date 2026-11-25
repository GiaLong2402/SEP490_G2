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
    @Mock
    private RawMaterialRepository rawMaterialRepository;

    private WarehouseService warehouseService;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(warehouseRepository, locationRepository, stockRepository, rawMaterialRepository);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-1")).thenReturn(true);

        assertThatThrownBy(() -> warehouseService.create(warehouseRequest("KHO-1", "Kho 1", 100, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingCapacity() {
        assertThatThrownBy(() -> warehouseService.create(warehouseRequest("KHO-1", "Kho 1", null, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void create_trimsAndPersists() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-1")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            return withId(w, 10L);
        });
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(10L)).thenReturn(List.of());

        WarehouseResponse response = warehouseService.create(warehouseRequest(" KHO-1 ", " Kho chính ", 200, true));

        assertThat(response.code()).isEqualTo("KHO-1");
        assertThat(response.name()).isEqualTo("Kho chính");
        assertThat(response.capacity()).isEqualTo(200);
        assertThat(response.isActive()).isTrue();
        assertThat(response.totalQty()).isZero();
        assertThat(response.allocatedCapacity()).isZero();
    }

    @Test
    void update_blocksDeactivateWhenStockRemains() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByCodeIgnoreCaseAndIdNot("KHO-1", 1L)).thenReturn(false);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(5L);

        assertThatThrownBy(() -> warehouseService.update(1L, warehouseRequest("KHO-1", "Kho 1", 100, false)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void update_rejectsCapacityBelowUsedQty() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByCodeIgnoreCaseAndIdNot("KHO-1", 1L)).thenReturn(false);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(80L);
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> warehouseService.update(1L, warehouseRequest("KHO-1", "Kho 1", 70, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("hàng đang chứa");
                });
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void update_rejectsCapacityBelowAllocatedLocations() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        Location a = withLocationId(newLocation(warehouse, "A-01", 40), 2L);
        Location b = withLocationId(newLocation(warehouse, "A-02", 40), 3L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByCodeIgnoreCaseAndIdNot("KHO-1", 1L)).thenReturn(false);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(10L);
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of(a, b));

        assertThatThrownBy(() -> warehouseService.update(1L, warehouseRequest("KHO-1", "Kho 1", 70, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("tổng sức chứa các vị trí");
                });
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void create_rawKind_isStoredAndReturned() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-NL")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> withId(inv.getArgument(0), 11L));
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(11L)).thenReturn(List.of());

        WarehouseResponse response = warehouseService.create(
                new WarehouseRequest("KHO-NL", "Kho nguyên liệu", null, 500, true, "raw"));

        assertThat(response.kind()).isEqualTo(WarehouseKind.RAW);
    }

    @Test
    void create_rejectsUnknownKind() {
        when(warehouseRepository.existsByCodeIgnoreCase("KHO-X")).thenReturn(false);

        assertThatThrownBy(() -> warehouseService.create(new WarehouseRequest("KHO-X", "Kho X", null, 100, true, "lanh")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void update_rejectsKindChangeWhenWarehouseHasLocations() {
        Warehouse warehouse = withId(newWarehouse("KHO-TP", "Kho thành phẩm", 100, true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByCodeIgnoreCaseAndIdNot("KHO-TP", 1L)).thenReturn(false);
        when(stockRepository.sumQtyByWarehouseId(1L)).thenReturn(0L);
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L))
                .thenReturn(List.of(newLocation(warehouse, "CHA", 50)));

        assertThatThrownBy(() -> warehouseService.update(
                        1L, new WarehouseRequest("KHO-TP", "Kho thành phẩm", null, 100, true, "raw")))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    void deleteLocation_blocksWhenRawMaterialsUseZone() {
        Warehouse raw = withId(newWarehouse("KHO-NL", "Kho nguyên liệu", 500, true), 2L);
        raw.setKind(WarehouseKind.RAW);
        Location cold = withLocationId(newLocation(raw, "LANH", 1), 8L);
        when(locationRepository.findById(8L)).thenReturn(Optional.of(cold));
        when(stockRepository.existsByLocation_Id(8L)).thenReturn(false);
        when(rawMaterialRepository.countByStorageLocation_Id(8L)).thenReturn(3L);

        assertThatThrownBy(() -> warehouseService.deleteLocation(8L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 nguyên liệu");
        verify(locationRepository, never()).delete(any());
    }

    @Test
    void list_rawWarehouse_countsMaterialsPerZoneAndComesLast() {
        Warehouse raw = withId(newWarehouse("KHO-NL", "Kho nguyên liệu", 500, true), 2L);
        raw.setKind(WarehouseKind.RAW);
        Warehouse finished = withId(newWarehouse("KHO-TP", "Kho thành phẩm", 500, true), 1L);
        Location cold = withLocationId(newLocation(raw, "LANH", 1), 8L);
        when(warehouseRepository.findAll(any(Sort.class))).thenReturn(List.of(raw, finished));
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of());
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(2L)).thenReturn(List.of(cold));
        when(stockRepository.sumQtyByLocationId(8L)).thenReturn(0L);
        when(stockRepository.existsByLocation_Id(8L)).thenReturn(false);
        when(rawMaterialRepository.countByStorageLocation_Id(8L)).thenReturn(2L);

        List<WarehouseResponse> list = warehouseService.list();

        assertThat(list).extracting(WarehouseResponse::code).containsExactly("KHO-TP", "KHO-NL");
        assertThat(list.get(1).kind()).isEqualTo(WarehouseKind.RAW);
        assertThat(list.get(1).locations().get(0).materialCount()).isEqualTo(2L);
        assertThat(list.get(1).locations().get(0).canDelete()).isFalse();
    }

    @Test
    void deleteLocation_blocksWhenStockExists() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        Location location = withLocationId(newLocation(warehouse, "A-01", 20), 7L);
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
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        assertThatThrownBy(() -> warehouseService.addLocation(1L, new LocationRequest("A-01", null, 0, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void addLocation_rejectsWhenSumExceedsWarehouse() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        Location existing = withLocationId(newLocation(warehouse, "A-01", 80), 2L);
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(locationRepository.existsByWarehouse_IdAndCodeIgnoreCase(1L, "A-02")).thenReturn(false);
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of(existing));

        assertThatThrownBy(() -> warehouseService.addLocation(1L, new LocationRequest("A-02", null, 30, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("vượt sức chứa kho");
                });
        verify(locationRepository, never()).save(any());
    }

    @Test
    void updateLocation_rejectsCapacityBelowUsed() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 100, true), 1L);
        Location location = withLocationId(newLocation(warehouse, "A-01", 40), 7L);
        when(locationRepository.findById(7L)).thenReturn(Optional.of(location));
        when(locationRepository.existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(1L, "A-01", 7L)).thenReturn(false);
        when(stockRepository.sumQtyByLocationId(7L)).thenReturn(25L);

        assertThatThrownBy(() -> warehouseService.updateLocation(7L, new LocationRequest("A-01", null, 20, null)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(locationRepository, never()).save(any());
    }

    @Test
    void list_includesUsedQtyAndCanDelete() {
        Warehouse warehouse = withId(newWarehouse("KHO-1", "Kho 1", 200, true), 1L);
        Location empty = withLocationId(newLocation(warehouse, "A-01", 50), 2L);
        Location used = withLocationId(newLocation(warehouse, "B-01", 50), 3L);
        when(warehouseRepository.findAll(Sort.by(Sort.Direction.ASC, "code"))).thenReturn(List.of(warehouse));
        when(locationRepository.findByWarehouse_IdOrderByCodeAsc(1L)).thenReturn(List.of(empty, used));
        when(stockRepository.sumQtyByLocationId(2L)).thenReturn(0L);
        when(stockRepository.sumQtyByLocationId(3L)).thenReturn(12L);
        when(stockRepository.existsByLocation_Id(2L)).thenReturn(false);
        when(stockRepository.existsByLocation_Id(3L)).thenReturn(true);

        List<WarehouseResponse> list = warehouseService.list();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).capacity()).isEqualTo(200);
        assertThat(list.get(0).allocatedCapacity()).isEqualTo(100L);
        assertThat(list.get(0).totalQty()).isEqualTo(12L);
        assertThat(list.get(0).locations().get(0).canDelete()).isTrue();
        assertThat(list.get(0).locations().get(1).canDelete()).isFalse();
        assertThat(list.get(0).locations().get(1).usedQty()).isEqualTo(12L);
    }

    private static WarehouseRequest warehouseRequest(String code, String name, Integer capacity, boolean active) {
        return new WarehouseRequest(code, name, null, capacity, active, null);
    }

    private static Warehouse newWarehouse(String code, String name, int capacity, boolean active) {
        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setCapacity(capacity);
        warehouse.setActive(active);
        return warehouse;
    }

    private static Location newLocation(Warehouse warehouse, String code, int capacity) {
        Location location = new Location();
        location.setWarehouse(warehouse);
        location.setCode(code);
        location.setCapacity(capacity);
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
