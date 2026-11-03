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
        assertWarehouseCodeAvailable(code, null);

        Warehouse warehouse = new Warehouse();
        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setAddress(Strings.trimToNull(request.address()));
        warehouse.setActive(Boolean.TRUE.equals(request.isActive()));
        return toDetail(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho"));

        String code = requireWarehouseCode(request.code());
        String name = requireWarehouseName(request.name());
        assertWarehouseCodeAvailable(code, id);

        boolean deactivating = warehouse.isActive() && !Boolean.TRUE.equals(request.isActive());
        if (deactivating && stockRepository.sumQtyByWarehouseId(id) > 0) {
            throw ApiException.conflict("Kho còn hàng, hãy chuyển hết hàng trước khi ngừng dùng");
        }

        warehouse.setCode(code);
        warehouse.setName(name);
        warehouse.setAddress(Strings.trimToNull(request.address()));
        warehouse.setActive(Boolean.TRUE.equals(request.isActive()));
        return toDetail(warehouseRepository.save(warehouse));
    }

    @Transactional
    public void addLocation(Long warehouseId, LocationRequest request) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho"));
        String code = requireLocationCode(request.code());
        Integer capacity = normalizeCapacity(request.capacity());
        assertLocationCodeAvailable(warehouseId, code, null);

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
        Long warehouseId = location.getWarehouse().getId();
        String code = requireLocationCode(request.code());
        Integer capacity = normalizeCapacity(request.capacity());
        assertLocationCodeAvailable(warehouseId, code, id);

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

    private static Integer normalizeCapacity(Integer capacity) {
        if (capacity == null) {
            return null;
        }
        if (capacity <= 0) {
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
