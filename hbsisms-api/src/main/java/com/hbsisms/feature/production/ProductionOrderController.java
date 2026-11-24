package com.hbsisms.feature.production;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.production.dto.ProductionBomProductResponse;
import com.hbsisms.feature.production.dto.ProductionMaterialPreviewResponse;
import com.hbsisms.feature.production.dto.ProductionOrderCompleteRequest;
import com.hbsisms.feature.production.dto.ProductionOrderCreateRequest;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.production.dto.ProductionOrderUpdateRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/production-orders")
@PreAuthorize("hasAuthority('inventory')")
public class ProductionOrderController {

    private final ProductionOrderService productionOrderService;
    private final ProductionPlanningService productionPlanningService;

    public ProductionOrderController(
            ProductionOrderService productionOrderService,
            ProductionPlanningService productionPlanningService) {
        this.productionOrderService = productionOrderService;
        this.productionPlanningService = productionPlanningService;
    }

    @GetMapping
    public PageResponse<ProductionOrderResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return productionOrderService.list(q, status, page, pageSize);
    }

    /** Inter1 #48 note: list SP đã có BOM — quyền inventory (không cần products). */
    @GetMapping("/bom-products")
    public List<ProductionBomProductResponse> bomProducts() {
        return productionPlanningService.listBomProducts();
    }

    /** Inter1 #48 note: xem trước NVL cần dùng + tồn dùng được (bỏ lô hết hạn). */
    @GetMapping("/material-preview")
    public ProductionMaterialPreviewResponse materialPreview(
            @RequestParam Long productId, @RequestParam BigDecimal plannedQty) {
        return productionPlanningService.previewMaterials(productId, plannedQty);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductionOrderResponse create(
            @Valid @RequestBody ProductionOrderCreateRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return productionOrderService.create(request, actor == null ? null : actor.id());
    }

    @GetMapping("/{id}")
    public ProductionOrderResponse get(@PathVariable Long id) {
        return productionOrderService.get(id);
    }

    @PutMapping("/{id}")
    public ProductionOrderResponse update(
            @PathVariable Long id, @Valid @RequestBody ProductionOrderUpdateRequest request) {
        return productionOrderService.update(id, request);
    }

    @PostMapping("/{id}/approve")
    public ProductionOrderResponse approve(
            @PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return productionOrderService.approve(id, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/cancel")
    public ProductionOrderResponse cancel(@PathVariable Long id) {
        return productionOrderService.cancel(id);
    }

    /** Task 51 (Cường): báo hoàn thành + sinh phiếu nhập TP Pending. */
    @PostMapping("/{id}/complete")
    public ProductionOrderResponse complete(
            @PathVariable Long id,
            @Valid @RequestBody ProductionOrderCompleteRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return productionOrderService.complete(id, request, actor == null ? null : actor.id());
    }
}
