package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.production.dto.ProductionOrderCreateRequest;
import com.hbsisms.feature.production.dto.ProductionOrderMaterialLineResponse;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.production.dto.ProductionOrderUpdateRequest;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductionOrderService {

    private final ProductionOrderRepository productionOrderRepository;
    private final ProductionOrderMaterialLineRepository materialLineRepository;
    private final ProductRepository productRepository;
    private final BomExplosionService bomExplosionService;
    private final MaterialIssueService materialIssueService;
    private final BusinessCodeService businessCodeService;
    private final UserRepository userRepository;

    public ProductionOrderService(
            ProductionOrderRepository productionOrderRepository,
            ProductionOrderMaterialLineRepository materialLineRepository,
            ProductRepository productRepository,
            BomExplosionService bomExplosionService,
            MaterialIssueService materialIssueService,
            BusinessCodeService businessCodeService,
            UserRepository userRepository) {
        this.productionOrderRepository = productionOrderRepository;
        this.materialLineRepository = materialLineRepository;
        this.productRepository = productRepository;
        this.bomExplosionService = bomExplosionService;
        this.materialIssueService = materialIssueService;
        this.businessCodeService = businessCodeService;
        this.userRepository = userRepository;
    }

    public PageResponse<ProductionOrderResponse> list(String q, String statusCode, int page, int pageSize) {
        ProductionOrderStatus status = null;
        if (statusCode != null && !statusCode.isBlank()) {
            try {
                status = ProductionOrderStatus.fromCode(statusCode);
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest(ex.getMessage());
            }
        }
        String query = q == null || q.isBlank() ? null : q.trim();
        Page<ProductionOrder> result = productionOrderRepository.search(
                query, status, PageRequests.of(page, pageSize, Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(result.map(this::toResponse));
    }

    public ProductionOrderResponse get(Long id) {
        return toResponse(requireOrder(id));
    }

    @Transactional
    public ProductionOrderResponse create(ProductionOrderCreateRequest request, Long actorId) {
        Product product = productRepository
                .findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        if (!product.isActive()) {
            throw ApiException.badRequest("Sản phẩm đã ngừng kinh doanh");
        }

        BomExplosionService.ExplosionResult explosion =
                bomExplosionService.explode(product.getId(), request.plannedQty());
        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);

        ProductionOrder order = new ProductionOrder();
        order.setCode(businessCodeService.nextProductionOrderCode());
        order.setProduct(product);
        order.setPlannedQty(request.plannedQty());
        order.setPlannedDueDate(request.plannedDueDate());
        order.setNote(blankToNull(request.note()));
        order.setEstimatedProductionCost(request.estimatedProductionCost());
        order.setEstimatedMaterialCost(explosion.estimatedMaterialCost());
        order.setStatus(ProductionOrderStatus.DRAFT);
        order.setCreatedBy(actor);
        order.replaceMaterialLines(explosion.lines());

        ProductionOrder saved = productionOrderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderUpdateRequest request) {
        ProductionOrder order = requireOrder(id);
        if (order.getStatus() != ProductionOrderStatus.DRAFT) {
            throw ApiException.conflict("Chỉ sửa được lệnh đang ở trạng thái bản nháp");
        }

        BomExplosionService.ExplosionResult explosion =
                bomExplosionService.explode(order.getProduct().getId(), request.plannedQty());
        order.setPlannedQty(request.plannedQty());
        order.setPlannedDueDate(request.plannedDueDate());
        order.setNote(blankToNull(request.note()));
        order.setEstimatedProductionCost(request.estimatedProductionCost());
        order.setEstimatedMaterialCost(explosion.estimatedMaterialCost());
        order.replaceMaterialLines(explosion.lines());
        return toResponse(order);
    }

    /** Task 49: duyệt lệnh + tự sinh phiếu xuất NVL PENDING. */
    @Transactional
    public ProductionOrderResponse approve(Long id, Long actorId) {
        ProductionOrder order = requireOrder(id);
        if (order.getStatus() != ProductionOrderStatus.DRAFT) {
            throw ApiException.conflict("Chỉ duyệt được lệnh đang ở trạng thái bản nháp");
        }
        // Refresh stock snapshot before approve
        BomExplosionService.ExplosionResult explosion =
                bomExplosionService.explode(order.getProduct().getId(), order.getPlannedQty());
        order.setEstimatedMaterialCost(explosion.estimatedMaterialCost());
        order.replaceMaterialLines(explosion.lines());

        if (!order.allMaterialsSufficient()) {
            throw ApiException.conflict("Không đủ nguyên liệu để duyệt lệnh sản xuất");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        order.setStatus(ProductionOrderStatus.APPROVED);
        order.setApprovedAt(Instant.now());
        order.setApprovedBy(actor);
        materialIssueService.createPendingFromOrder(order, actorId);
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse cancel(Long id) {
        ProductionOrder order = requireOrder(id);
        if (order.getStatus() == ProductionOrderStatus.CANCELLED) {
            return toResponse(order);
        }
        if (order.getStatus() != ProductionOrderStatus.DRAFT
                && order.getStatus() != ProductionOrderStatus.APPROVED) {
            throw ApiException.conflict("Không thể hủy lệnh ở trạng thái hiện tại");
        }
        MaterialIssue issue = order.getMaterialIssue();
        if (issue != null && issue.getStatus() == MaterialIssueStatus.COMPLETED) {
            throw ApiException.conflict("Phiếu xuất NVL đã hoàn thành — không thể hủy lệnh");
        }
        materialIssueService.cancelIfPending(issue);
        order.setStatus(ProductionOrderStatus.CANCELLED);
        return toResponse(order);
    }

    private ProductionOrder requireOrder(Long id) {
        return productionOrderRepository
                .findWithHeaderById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lệnh sản xuất"));
    }

    private ProductionOrderResponse toResponse(ProductionOrder order) {
        List<ProductionOrderMaterialLine> lines = order.getMaterialLines();
        if (lines == null || lines.isEmpty()) {
            lines = materialLineRepository.findByProductionOrder_IdOrderBySortOrderAscIdAsc(order.getId());
        }
        List<ProductionOrderMaterialLineResponse> materials =
                lines.stream().map(ProductionOrderMaterialLineResponse::from).toList();
        return ProductionOrderResponse.from(order, materials);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
