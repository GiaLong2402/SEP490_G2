package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.production.dto.MaterialIssueLineResponse;
import com.hbsisms.feature.production.dto.MaterialIssueResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MaterialIssueService {

    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final BusinessCodeService businessCodeService;
    private final UserRepository userRepository;

    public MaterialIssueService(
            MaterialIssueRepository materialIssueRepository,
            MaterialIssueLineRepository materialIssueLineRepository,
            BusinessCodeService businessCodeService,
            UserRepository userRepository) {
        this.materialIssueRepository = materialIssueRepository;
        this.materialIssueLineRepository = materialIssueLineRepository;
        this.businessCodeService = businessCodeService;
        this.userRepository = userRepository;
    }

    public MaterialIssueResponse get(Long id) {
        MaterialIssue issue = materialIssueRepository
                .findWithHeaderById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu xuất NVL"));
        return toResponse(issue);
    }

    /**
     * Task 49: tạo phiếu xuất PENDING từ lệnh đã duyệt — chưa trừ tồn (task 50).
     */
    @Transactional
    public MaterialIssue createPendingFromOrder(ProductionOrder order, Long actorId) {
        materialIssueRepository.findByProductionOrder_Id(order.getId()).ifPresent(existing -> {
            throw ApiException.conflict("Lệnh sản xuất đã có phiếu xuất NVL: " + existing.getCode());
        });

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        MaterialIssue issue = new MaterialIssue();
        issue.setCode(businessCodeService.nextMaterialIssueCode());
        issue.setProductionOrder(order);
        issue.setStatus(MaterialIssueStatus.PENDING);
        issue.setCreatedBy(actor);

        List<MaterialIssueLine> lines = new ArrayList<>();
        for (ProductionOrderMaterialLine mol : order.getMaterialLines()) {
            MaterialIssueLine line = new MaterialIssueLine();
            line.setRawMaterial(mol.getRawMaterial());
            line.setQtyRequired(mol.getRequiredQty());
            lines.add(line);
        }
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Lệnh sản xuất không có dòng nguyên liệu");
        }
        issue.replaceLines(lines);
        MaterialIssue saved = materialIssueRepository.save(issue);
        order.setMaterialIssue(saved);
        return saved;
    }

    @Transactional
    public void cancelIfPending(MaterialIssue issue) {
        if (issue == null) {
            return;
        }
        if (issue.getStatus() == MaterialIssueStatus.COMPLETED) {
            throw ApiException.conflict("Phiếu xuất NVL đã hoàn thành — không thể hủy");
        }
        if (issue.getStatus() == MaterialIssueStatus.PENDING) {
            issue.setStatus(MaterialIssueStatus.CANCELLED);
        }
    }

    private MaterialIssueResponse toResponse(MaterialIssue issue) {
        List<MaterialIssueLineResponse> lines = materialIssueLineRepository
                .findByMaterialIssue_IdOrderBySortOrderAscIdAsc(issue.getId())
                .stream()
                .map(MaterialIssueLineResponse::from)
                .toList();
        return MaterialIssueResponse.from(issue, lines);
    }
}
