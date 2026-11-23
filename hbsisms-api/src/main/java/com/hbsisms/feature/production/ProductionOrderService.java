package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptService;
import com.hbsisms.feature.production.dto.ProductionOrderCompleteRequest;
import com.hbsisms.feature.production.dto.ProductionOrderCreateRequest;
import com.hbsisms.feature.production.dto.ProductionOrderMaterialLineResponse;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.production.dto.ProductionOrderUpdateRequest;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
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
    private final ProductVariantRepository productVariantRepository;
    private final BomExplosionService bomExplosionService;
    private final MaterialIssueService materialIssueService;
    private final GoodsReceiptService goodsReceiptService;
    private final BusinessCodeService businessCodeService;
    private final UserRepository userRepository;

    public ProductionOrderService(
            ProductionOrderRepository productionOrderRepository,
            ProductionOrderMaterialLineRepository materialLineRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            BomExplosionService bomExplosionService,
            MaterialIssueService materialIssueService,
            GoodsReceiptService goodsReceiptService,
            BusinessCodeService businessCodeService,
            UserRepository userRepository) {
        this.productionOrderRepository = productionOrderRepository;
        this.materialLineRepository = materialLineRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.bomExplosionService = bomExplosionService;
        this.materialIssueService = materialIssueService;
        this.goodsReceiptService = goodsReceiptService;
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

    /**
     * Task 51: báo hoàn thành SX + sinh phiếu nhập TP Pending.
     * Đã có finishedReceipt → trả detail (idempotent).
     */
    @Transactional
    public ProductionOrderResponse complete(Long id, ProductionOrderCompleteRequest request, Long actorId) {
        if (request == null || request.actualQty() == null || request.actualQty().signum() <= 0) {
            throw ApiException.badRequest("Số lượng thành phẩm phải lớn hơn 0");
        }
        ProductionOrder order = productionOrderRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lệnh sản xuất"));

        if (order.getFinishedReceipt() != null
                || order.getStatus() == ProductionOrderStatus.AWAITING_RECEIPT) {
            return toResponse(requireOrder(id));
        }
        if (order.getStatus() != ProductionOrderStatus.IN_PROGRESS) {
            throw ApiException.conflict("Chỉ báo hoàn thành khi lệnh đang sản xuất (đã xuất NVL)");
        }
        MaterialIssue issue = order.getMaterialIssue();
        if (issue == null || issue.getStatus() != MaterialIssueStatus.COMPLETED) {
            throw ApiException.conflict("Phiếu xuất NVL chưa hoàn thành — không thể báo hoàn thành");
        }

        ProductVariant variant = resolveFinishedVariant(order, request.variantId());
        order.setActualQty(request.actualQty());
        order.setActualProductionCost(
                request.actualProductionCost() == null ? BigDecimal.ZERO : request.actualProductionCost());
        order.setFinishedVariant(variant);

        GoodsReceipt receipt = goodsReceiptService.createPendingFinishedFromProduction(
                order.getId(), order.getCode(), variant, request.actualQty(), actorId);
        order.setFinishedReceipt(receipt);
        order.setStatus(ProductionOrderStatus.AWAITING_RECEIPT);
        productionOrderRepository.save(order);
        return toResponse(requireOrder(id));
    }

    private ProductVariant resolveFinishedVariant(ProductionOrder order, Long variantId) {
        if (variantId != null) {
            ProductVariant variant = productVariantRepository
                    .findById(variantId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));
            if (!variant.getProduct().getId().equals(order.getProduct().getId())) {
                throw ApiException.badRequest("Quy cách không thuộc sản phẩm của lệnh");
            }
            if (!variant.isActive()) {
                throw ApiException.conflict("Quy cách đã ngừng bán");
            }
            return variant;
        }
        return productVariantRepository.findByProduct_IdOrderByIdAsc(order.getProduct().getId()).stream()
                .filter(ProductVariant::isActive)
                .findFirst()
                .orElseThrow(() -> ApiException.conflict(
                        "Sản phẩm chưa có quy cách đang bán — chọn variantId khi báo hoàn thành"));
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
