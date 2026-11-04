package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.inventory.dto.LocationRequest;
import com.hbsisms.feature.inventory.dto.LocationResponse;
import com.hbsisms.feature.inventory.dto.WarehouseRequest;
import com.hbsisms.feature.inventory.dto.WarehouseResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final StockRepository stockRepository;

    public WarehouseService(
            WarehouseRepository warehouseRepository,
            LocationRepository locationRepository,
            StockRepository stockRepository) {
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.stockRepository = stockRepository;
    }

    public List<WarehouseResponse> list() {
        List<Warehouse> warehouses = warehouseRepository.findAll(Sort.by(Sort.Direction.ASC, "code"));
        List<WarehouseResponse> result = new ArrayList<>(warehouses.size());
        for (Warehouse warehouse : warehouses) {
            result.add(toDetail(warehouse));
        }
        return result;
    }

    @Transactional
    public WarehouseResponse create(WarehouseRequest request) {
        String code = requireWarehouseCode(request.code());
        String name = requireWarehouseName(request.name());
        int capacity = requirePositiveCapacity(request.capacity());
        assertWarehouseCodeAvailable(code, null);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setAddress(Strings.trimToNull(request.address()));
        warehouse.setCapacity(capacity);
        warehouse.setActive(Boolean.TRUE.equals(request.isActive()));
        return toDetail(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho"));

        String code = requireWarehouseCode(request.code());
        String name = requireWarehouseName(request.name());
        int capacity = requirePositiveCapacity(request.capacity());
        assertWarehouseCodeAvailable(code, id);

        boolean deactivating = warehouse.isActive() && !Boolean.TRUE.equals(request.isActive());
        long usedQty = stockRepository.sumQtyByWarehouseId(id);
        if (deactivating && usedQty > 0) {
            throw ApiException.conflict("Kho còn hàng, hãy chuyển hết hàng trước khi ngừng dùng");
        }

        long allocated = sumLocationCapacities(id, null, 0);
        if (capacity < usedQty) {
            throw ApiException.conflict(String.format(
                    "Sức chứa kho phải ≥ hàng đang chứa (%d khay), không thể đặt %d.",
                    usedQty,
                    capacity));
        }
        if (capacity < allocated) {
            throw ApiException.conflict(String.format(
                    "Sức chứa kho phải ≥ tổng sức chứa các vị trí (%d khay), không thể đặt %d.",
                    allocated,
                    capacity));
        }

        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setAddress(Strings.trimToNull(request.address()));
        warehouse.setCapacity(capacity);
        warehouse.setActive(Boolean.TRUE.equals(request.isActive()));
        return toDetail(warehouseRepository.save(warehouse));
    }

    @Transactional
    public void addLocation(Long warehouseId, LocationRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho"));
        String code = requireLocationCode(request.code());
        int capacity = requirePositiveCapacity(request.capacity());
        assertLocationCodeAvailable(warehouseId, code, null);
        assertLocationFitsWarehouse(warehouse, warehouseId, null, capacity);

        Location location = new Location();
        location.setWarehouse(warehouse);
        location.setCode(code);
        location.setZone(Strings.trimToNull(request.zone()));
        location.setCapacity(capacity);
        location.setNote(Strings.trimToNull(request.note()));
        locationRepository.save(location);
    }

    @Transactional
    public void updateLocation(Long id, LocationRequest request) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
        Warehouse warehouse = location.getWarehouse();
        Long warehouseId = warehouse.getId();
        String code = requireLocationCode(request.code());
        int capacity = requirePositiveCapacity(request.capacity());
        assertLocationCodeAvailable(warehouseId, code, id);

        long usedQty = stockRepository.sumQtyByLocationId(id);
        if (capacity < usedQty) {
            throw ApiException.conflict(String.format(
                    "Sức chứa vị trí phải ≥ hàng đang chứa (%d khay), không thể đặt %d.",
                    usedQty,
                    capacity));
        }
        assertLocationFitsWarehouse(warehouse, warehouseId, id, capacity);

        location.setCode(code);
        location.setZone(Strings.trimToNull(request.zone()));
        location.setCapacity(capacity);
        location.setNote(Strings.trimToNull(request.note()));
        locationRepository.save(location);
    }

    @Transactional
    public void deleteLocation(Long id) {
        Location location = locationRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
        if (stockRepository.existsByLocation_Id(id)) {
            throw ApiException.conflict("Vị trí đã có dữ liệu tồn kho, không thể xóa");
        }
        locationRepository.delete(location);
    }

    private WarehouseResponse toDetail(Warehouse warehouse) {
        List<Location> locations = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId());
        List<LocationResponse> locationResponses = new ArrayList<>(locations.size());
        for (Location location : locations) {
            long usedQty = stockRepository.sumQtyByLocationId(location.getId());
            boolean canDelete = !stockRepository.existsByLocation_Id(location.getId());
            locationResponses.add(LocationResponse.from(location, usedQty, canDelete));
        }
        return WarehouseResponse.from(warehouse, locationResponses);
    }

    /**
     * Sum of location capacities in a warehouse, optionally replacing one location's capacity
     * with {@code newCapacity} (for add: {@code excludeLocationId == null}).
     */
    private long sumLocationCapacities(Long warehouseId, Long excludeLocationId, int newCapacity) {
        long sum = 0;
        for (Location location : locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouseId)) {
            if (excludeLocationId != null && excludeLocationId.equals(location.getId())) {
                continue;
            }
            Integer cap = location.getCapacity();
            sum += cap != null ? cap : 0;
        }
        return sum + newCapacity;
    }

    private void assertLocationFitsWarehouse(
            Warehouse warehouse, Long warehouseId, Long excludeLocationId, int newCapacity) {
        long allocated = sumLocationCapacities(warehouseId, excludeLocationId, newCapacity);
        int warehouseCapacity = warehouse.getCapacity() != null ? warehouse.getCapacity() : 0;
        if (allocated > warehouseCapacity) {
            long others = allocated - newCapacity;
            long remaining = Math.max(0, warehouseCapacity - others);
            throw ApiException.conflict(String.format(
                    "Tổng sức chứa vị trí (%d) vượt sức chứa kho %s (%d). Còn phân bổ được tối đa %d khay.",
                    allocated,
                    warehouse.getCode(),
                    warehouseCapacity,
                    remaining));
        }
    }

    private static String requireWarehouseCode(String code) {
        String trimmed = Strings.trimToNull(code);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã và tên kho");
        }
        return trimmed;
    }

    private static String requireWarehouseName(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã và tên kho");
        }
        return trimmed;
    }

    private static String requireLocationCode(String code) {
        String trimmed = Strings.trimToNull(code);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã vị trí");
        }
        return trimmed;
    }

    private static int requirePositiveCapacity(Integer capacity) {
        if (capacity == null || capacity <= 0) {
            throw ApiException.badRequest("Sức chứa phải là số nguyên dương");
        }
        return capacity;
    }

    private void assertWarehouseCodeAvailable(String code, Long currentId) {
        boolean taken = currentId == null
                ? warehouseRepository.existsByCodeIgnoreCase(code)
                : warehouseRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (taken) {
            throw ApiException.conflict("Mã kho " + code + " đã tồn tại");
        }
    }

    private void assertLocationCodeAvailable(Long warehouseId, String code, Long currentId) {
        boolean taken = currentId == null
                ? locationRepository.existsByWarehouse_IdAndCodeIgnoreCase(warehouseId, code)
                : locationRepository.existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(warehouseId, code, currentId);
        if (taken) {
            throw ApiException.conflict("Vị trí " + code + " đã có trong kho này");
        }
    }
}
