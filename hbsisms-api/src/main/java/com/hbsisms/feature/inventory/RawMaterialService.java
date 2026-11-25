package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.UnitOfMeasure;
import com.hbsisms.feature.catalog.UnitOfMeasureRepository;
import com.hbsisms.feature.inventory.dto.RawMaterialRequest;
import com.hbsisms.feature.inventory.dto.RawMaterialResponse;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RawMaterialService {

    private final RawMaterialRepository rawMaterialRepository;
    private final UnitOfMeasureRepository unitRepository;
    private final LocationRepository locationRepository;

    public RawMaterialService(
            RawMaterialRepository rawMaterialRepository,
            UnitOfMeasureRepository unitRepository,
            LocationRepository locationRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.unitRepository = unitRepository;
        this.locationRepository = locationRepository;
    }

    public List<RawMaterialResponse> list(String q, Boolean activeOnly, Boolean lowStock) {
        return list(q, activeOnly, lowStock, null);
    }

    /** {@code storageLocationId} lọc theo khu cất (Khu lạnh / Khu khô) trong kho nguyên liệu. */
    public List<RawMaterialResponse> list(String q, Boolean activeOnly, Boolean lowStock, Long storageLocationId) {
        String needle = Strings.trimToNull(q);
        String pattern = needle == null ? null : needle.toLowerCase(Locale.ROOT);
        return rawMaterialRepository.findAllByOrderByCodeAsc().stream()
                .filter(m -> pattern == null
                        || m.getCode().toLowerCase(Locale.ROOT).contains(pattern)
                        || m.getName().toLowerCase(Locale.ROOT).contains(pattern))
                .filter(m -> activeOnly == null || m.isActive() == activeOnly)
                .filter(m -> !Boolean.TRUE.equals(lowStock) || isLowStock(m))
                .filter(m -> storageLocationId == null
                        || (m.getStorageLocation() != null && storageLocationId.equals(m.getStorageLocation().getId())))
                .map(RawMaterialResponse::from)
                .toList();
    }

    public RawMaterialResponse get(Long id) {
        return RawMaterialResponse.from(require(id));
    }

    @Transactional
    public RawMaterialResponse create(RawMaterialRequest request) {
        String code = normalizeCode(request.code());
        if (rawMaterialRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        if (request.storageLocationId() == null) {
            throw ApiException.badRequest("Vui lòng chọn khu cất trong kho nguyên liệu");
        }
        RawMaterial material = new RawMaterial();
        apply(material, request, code);
        material.setQtyOnHand(BigDecimal.ZERO);
        return RawMaterialResponse.from(rawMaterialRepository.save(material));
    }

    @Transactional
    public RawMaterialResponse update(Long id, RawMaterialRequest request) {
        RawMaterial material = require(id);
        String code = normalizeCode(request.code());
        if (rawMaterialRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        apply(material, request, code);
        return RawMaterialResponse.from(rawMaterialRepository.save(material));
    }

    private RawMaterial require(Long id) {
        return rawMaterialRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
    }

    static boolean isLowStock(RawMaterial material) {
        BigDecimal reorder = material.getReorderLevel();
        return reorder != null && material.getQtyOnHand().compareTo(reorder) < 0;
    }

    private void apply(RawMaterial material, RawMaterialRequest request, String code) {
        String name = Strings.trimToNull(request.name());
        if (name == null) {
            throw ApiException.badRequest("Vui lòng nhập mã, tên và đơn vị");
        }
        resolveUnit(material, request);
        if (request.reorderLevel() != null && request.reorderLevel().signum() < 0) {
            throw ApiException.badRequest("Mức đặt lại không được âm");
        }
        material.setCode(code);
        material.setName(name);
        material.setReorderLevel(request.reorderLevel());
        material.setActive(Boolean.TRUE.equals(request.isActive()));
        if (request.storageLocationId() != null) {
            material.setStorageLocation(requireRawStorage(request.storageLocationId()));
        }
    }

    /** Khu cất phải là vị trí của kho nguyên liệu, không cất nguyên liệu vào vị trí kho thành phẩm. */
    private Location requireRawStorage(Long locationId) {
        Location location = locationRepository
                .findById(locationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khu cất"));
        if (location.getWarehouse() == null || !location.getWarehouse().isRaw()) {
            throw ApiException.badRequest("Khu cất phải thuộc kho nguyên liệu");
        }
        return location;
    }

    /** Prefers {@code unitId}; falls back to legacy free-text {@code unit} (linked to master by code when found). */
    private void resolveUnit(RawMaterial material, RawMaterialRequest request) {
        if (request.unitId() != null) {
            UnitOfMeasure ref = unitRepository
                    .findById(request.unitId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn vị tính"));
            UnitOfMeasure current = material.getUnitRef();
            boolean sameAsCurrent = current != null && ref.getId().equals(current.getId());
            if (!ref.isActive() && !sameAsCurrent) {
                throw ApiException.badRequest("Đơn vị tính đã ngừng sử dụng");
            }
            material.setUnitRef(ref);
            material.setUnit(ref.getCode());
            return;
        }
        String legacy = Strings.trimToNull(request.unit());
        if (legacy == null) {
            throw ApiException.badRequest("Vui lòng nhập mã, tên và đơn vị");
        }
        UnitOfMeasure matched = unitRepository.findByCodeIgnoreCase(legacy).orElse(null);
        material.setUnitRef(matched);
        material.setUnit(matched == null ? legacy : matched.getCode());
    }

    private static String normalizeCode(String raw) {
        String code = Strings.trimToNull(raw);
        if (code == null) {
            throw ApiException.badRequest("Vui lòng nhập mã, tên và đơn vị");
        }
        return code;
    }
}
