package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.inventory.dto.RawMaterialRequest;
import com.hbsisms.feature.inventory.dto.RawMaterialResponse;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RawMaterialService {

    private final RawMaterialRepository rawMaterialRepository;

    public RawMaterialService(RawMaterialRepository rawMaterialRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
    }

    public List<RawMaterialResponse> list() {
        return rawMaterialRepository.findAllByOrderByCodeAsc().stream()
                .map(RawMaterialResponse::from)
                .toList();
    }

    @Transactional
    public RawMaterialResponse create(RawMaterialRequest request) {
        String code = normalizeCode(request.code());
        if (rawMaterialRepository.existsByCodeIgnoreCase(code)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        RawMaterial material = new RawMaterial();
        apply(material, request, code);
        material.setQtyOnHand(BigDecimal.ZERO);
        return RawMaterialResponse.from(rawMaterialRepository.save(material));
    }

    @Transactional
    public RawMaterialResponse update(Long id, RawMaterialRequest request) {
        RawMaterial material = rawMaterialRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
        String code = normalizeCode(request.code());
        if (rawMaterialRepository.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
        apply(material, request, code);
        return RawMaterialResponse.from(rawMaterialRepository.save(material));
    }

    private static void apply(RawMaterial material, RawMaterialRequest request, String code) {
        String name = Strings.trimToNull(request.name());
        String unit = Strings.trimToNull(request.unit());
        if (name == null || unit == null) {
            throw ApiException.badRequest("Vui lòng nhập mã, tên và đơn vị");
        }
        if (request.reorderLevel() != null && request.reorderLevel().signum() < 0) {
            throw ApiException.badRequest("Mức đặt lại không được âm");
        }
        material.setCode(code);
        material.setName(name);
        material.setUnit(unit);
        material.setReorderLevel(request.reorderLevel());
        material.setActive(Boolean.TRUE.equals(request.isActive()));
    }

    private static String normalizeCode(String raw) {
        String code = Strings.trimToNull(raw);
        if (code == null) {
            throw ApiException.badRequest("Vui lòng nhập mã, tên và đơn vị");
        }
        return code;
    }
}
