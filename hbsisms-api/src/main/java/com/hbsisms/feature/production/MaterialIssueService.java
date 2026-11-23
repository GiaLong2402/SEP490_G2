package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialRepository;
import com.hbsisms.feature.inventory.RawMaterialStockService;
import com.hbsisms.feature.inventory.RawMaterialStockService.IssueSlice;
import com.hbsisms.feature.production.dto.MaterialIssueAllocationRequest;
import com.hbsisms.feature.production.dto.MaterialIssueConfirmRequest;
import com.hbsisms.feature.production.dto.MaterialIssueLineResponse;
import com.hbsisms.feature.production.dto.MaterialIssueResponse;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MaterialIssueService {

    private final MaterialIssueRepository materialIssueRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final ProductionOrderRepository productionOrderRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final RawMaterialStockService rawMaterialStockService;
    private final BusinessCodeService businessCodeService;
    private final UserRepository userRepository;
    private final Clock clock;

    public MaterialIssueService(
            MaterialIssueRepository materialIssueRepository,
            MaterialIssueLineRepository materialIssueLineRepository,
            ProductionOrderRepository productionOrderRepository,
            RawMaterialRepository rawMaterialRepository,
            RawMaterialStockService rawMaterialStockService,
            BusinessCodeService businessCodeService,
            UserRepository userRepository,
            Clock clock) {
        this.materialIssueRepository = materialIssueRepository;
        this.materialIssueLineRepository = materialIssueLineRepository;
        this.productionOrderRepository = productionOrderRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.rawMaterialStockService = rawMaterialStockService;
        this.businessCodeService = businessCodeService;
        this.userRepository = userRepository;
        this.clock = clock;
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

    /**
     * Task 50: xác nhận xuất NVL — FEFO hoặc allocations tay, trừ tồn, lệnh → IN_PROGRESS.
     * Đã COMPLETED → trả response, không trừ thêm.
     */
    @Transactional
    public MaterialIssueResponse confirm(Long id, MaterialIssueConfirmRequest request, Long actorId) {
        MaterialIssue locked = materialIssueRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu xuất NVL"));

        if (locked.getStatus() == MaterialIssueStatus.COMPLETED) {
            return toResponse(materialIssueRepository
                    .findWithHeaderById(id)
                    .orElse(locked));
        }
        if (locked.getStatus() == MaterialIssueStatus.CANCELLED) {
            throw ApiException.conflict("Phiếu xuất NVL đã hủy");
        }
        if (locked.getStatus() != MaterialIssueStatus.PENDING) {
            throw ApiException.conflict("Không thể xác nhận phiếu xuất ở trạng thái hiện tại");
        }

        ProductionOrder order = productionOrderRepository
                .findByIdForUpdate(locked.getProductionOrder().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lệnh sản xuất"));
        if (order.getStatus() != ProductionOrderStatus.APPROVED) {
            throw ApiException.conflict("Chỉ xuất NVL khi lệnh đang ở trạng thái Đã duyệt");
        }

        List<MaterialIssueLine> requiredLines = materialIssueLineRepository
                .findByMaterialIssue_IdOrderBySortOrderAscIdAsc(locked.getId());
        if (requiredLines.isEmpty()) {
            throw ApiException.badRequest("Phiếu xuất không có dòng nguyên liệu");
        }

        Map<Long, BigDecimal> requiredByMaterial = new LinkedHashMap<>();
        for (MaterialIssueLine line : requiredLines) {
            requiredByMaterial.merge(line.getRawMaterial().getId(), line.getQtyRequired(), BigDecimal::add);
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        String note = "Xuất NVL phiếu " + locked.getCode() + " / lệnh " + order.getCode();

        List<MaterialIssueAllocationRequest> allocations =
                request == null || request.allocations() == null ? List.of() : request.allocations();

        List<MaterialIssueLine> issuedLines = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        int sort = 0;

        // Khóa NVL theo id tăng dần tránh deadlock.
        List<Long> materialIds = requiredByMaterial.keySet().stream().sorted().toList();
        Map<Long, RawMaterial> lockedMaterials = new LinkedHashMap<>();
        for (Long materialId : materialIds) {
            RawMaterial material = rawMaterialRepository
                    .findByIdForUpdate(materialId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
            lockedMaterials.put(materialId, material);
        }

        if (allocations.isEmpty()) {
            for (Map.Entry<Long, BigDecimal> entry : requiredByMaterial.entrySet()) {
                RawMaterial material = lockedMaterials.get(entry.getKey());
                List<IssueSlice> slices = rawMaterialStockService.issueFefo(
                        material,
                        entry.getValue(),
                        RawMaterialStockService.REF_MATERIAL_ISSUE,
                        locked.getId(),
                        actor,
                        note);
                boolean first = true;
                for (IssueSlice slice : slices) {
                    BigDecimal qtyRequiredOnLine = first ? entry.getValue() : BigDecimal.ZERO;
                    first = false;
                    issuedLines.add(toIssuedLine(material, qtyRequiredOnLine, slice, sort++));
                    totalCost = totalCost.add(costOf(slice));
                }
            }
        } else {
            validateAllocationsCover(requiredByMaterial, allocations);
            // Group allocations by material preserving order
            Map<Long, List<MaterialIssueAllocationRequest>> byMaterial = allocations.stream()
                    .collect(Collectors.groupingBy(
                            MaterialIssueAllocationRequest::rawMaterialId, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<Long, BigDecimal> required : requiredByMaterial.entrySet()) {
                Long materialId = required.getKey();
                RawMaterial material = lockedMaterials.get(materialId);
                BigDecimal materialRequired = required.getValue();
                List<MaterialIssueAllocationRequest> group =
                        byMaterial.getOrDefault(materialId, List.of());
                boolean first = true;
                for (MaterialIssueAllocationRequest alloc : group) {
                    IssueSlice slice = rawMaterialStockService.issueFromLot(
                            material,
                            alloc.lotId(),
                            alloc.qty(),
                            RawMaterialStockService.REF_MATERIAL_ISSUE,
                            locked.getId(),
                            actor,
                            note);
                    BigDecimal qtyRequiredOnLine = first ? materialRequired : BigDecimal.ZERO;
                    first = false;
                    issuedLines.add(toIssuedLine(material, qtyRequiredOnLine, slice, sort++));
                    totalCost = totalCost.add(costOf(slice));
                }
            }
        }

        locked.replaceLines(issuedLines);
        locked.setStatus(MaterialIssueStatus.COMPLETED);
        locked.setCompletedAt(Instant.now(clock));
        locked.setCompletedBy(actor);
        materialIssueRepository.save(locked);

        order.setStatus(ProductionOrderStatus.IN_PROGRESS);
        order.setActualMaterialCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        productionOrderRepository.save(order);

        return toResponse(locked);
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

    private static void validateAllocationsCover(
            Map<Long, BigDecimal> requiredByMaterial, List<MaterialIssueAllocationRequest> allocations) {
        Map<Long, BigDecimal> got = new LinkedHashMap<>();
        for (MaterialIssueAllocationRequest alloc : allocations) {
            if (alloc == null || alloc.rawMaterialId() == null || alloc.lotId() == null || alloc.qty() == null) {
                throw ApiException.badRequest("Phân bổ lô xuất không hợp lệ");
            }
            if (alloc.qty().signum() <= 0) {
                throw ApiException.badRequest("Số lượng xuất phải lớn hơn 0");
            }
            if (!requiredByMaterial.containsKey(alloc.rawMaterialId())) {
                throw ApiException.badRequest("Nguyên liệu không thuộc phiếu xuất");
            }
            got.merge(alloc.rawMaterialId(), alloc.qty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> required : requiredByMaterial.entrySet()) {
            BigDecimal issued = got.getOrDefault(required.getKey(), BigDecimal.ZERO);
            if (issued.compareTo(required.getValue()) != 0) {
                throw ApiException.conflict(
                        "Tổng xuất NVL #" + required.getKey()
                                + " phải đúng " + required.getValue()
                                + " (đang " + issued + ")");
            }
        }
        for (Long materialId : got.keySet()) {
            if (!requiredByMaterial.containsKey(materialId)) {
                throw ApiException.badRequest("Nguyên liệu không thuộc phiếu xuất");
            }
        }
    }

    private static MaterialIssueLine toIssuedLine(
            RawMaterial material, BigDecimal qtyRequired, IssueSlice slice, int sortOrder) {
        MaterialIssueLine line = new MaterialIssueLine();
        line.setRawMaterial(material);
        line.setQtyRequired(qtyRequired);
        line.setQtyIssued(slice.qty());
        line.setRawMaterialLot(slice.lot());
        line.setSortOrder(sortOrder);
        return line;
    }

    private static BigDecimal costOf(IssueSlice slice) {
        BigDecimal price = slice.unitCost() == null ? BigDecimal.ZERO : slice.unitCost();
        return price.multiply(slice.qty()).setScale(2, RoundingMode.HALF_UP);
    }

    private MaterialIssueResponse toResponse(MaterialIssue issue) {
        List<MaterialIssueLine> persisted =
                materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(issue.getId());
        List<MaterialIssueLine> source =
                (persisted == null || persisted.isEmpty()) && issue.getLines() != null && !issue.getLines().isEmpty()
                        ? issue.getLines()
                        : persisted;
        List<MaterialIssueLineResponse> lines = source.stream().map(MaterialIssueLineResponse::from).toList();
        return MaterialIssueResponse.from(issue, lines);
    }
}
