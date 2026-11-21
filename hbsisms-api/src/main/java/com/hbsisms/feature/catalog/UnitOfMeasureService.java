package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.dto.UnitOfMeasureRequest;
import com.hbsisms.feature.catalog.dto.UnitOfMeasureResponse;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository unitRepository;
    private final ProductRepository productRepository;
    private final RawMaterialRepository rawMaterialRepository;

    public UnitOfMeasureService(
            UnitOfMeasureRepository unitRepository,
            ProductRepository productRepository,
            RawMaterialRepository rawMaterialRepository) {
        this.unitRepository = unitRepository;
        this.productRepository = productRepository;
        this.rawMaterialRepository = rawMaterialRepository;
    }

    public List<UnitOfMeasureResponse> list(Boolean activeOnly) {
        return unitRepository.findAllByOrderByCodeAsc().stream()
                .filter(u -> activeOnly == null || u.isActive() == activeOnly)
                .map(UnitOfMeasureResponse::from)
                .toList();
    }

    @Transactional
    public UnitOfMeasureResponse create(UnitOfMeasureRequest request) {
        String code = requireCode(request.code());
        String name = requireName(request.name());
        if (unitRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        UnitOfMeasure unit = new UnitOfMeasure();
        unit.setCode(code);
        unit.setName(name);
        unit.setActive(Boolean.TRUE.equals(request.isActive()));
        return UnitOfMeasureResponse.from(unitRepository.save(unit));
    }

    @Transactional
    public UnitOfMeasureResponse update(Long id, UnitOfMeasureRequest request) {
        UnitOfMeasure unit = unitRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn vị tính"));
        String code = requireCode(request.code());
        String name = requireName(request.name());
        if (unitRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        boolean active = Boolean.TRUE.equals(request.isActive());
        if (unit.isActive() && !active && isInUse(id)) {
            throw ApiException.conflict("Không thể ngừng sử dụng đơn vị đang được dùng bởi sản phẩm hoặc nguyên liệu");
        }
        unit.setCode(code);
        unit.setName(name);
        unit.setActive(active);
        return UnitOfMeasureResponse.from(unitRepository.save(unit));
    }

    private boolean isInUse(Long unitId) {
        return productRepository.existsByUnit_Id(unitId) || rawMaterialRepository.existsByUnitRef_Id(unitId);
    }

    private static String requireCode(String code) {
        String trimmed = Strings.trimToNull(code);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã đơn vị");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập tên đơn vị");
        }
        return trimmed;
    }
}
