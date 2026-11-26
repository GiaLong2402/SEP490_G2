package com.hbsisms.feature.production;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.PackSizes;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.GoodsReceipt;
import com.hbsisms.feature.inventory.GoodsReceiptService;
import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialStockService;
import com.hbsisms.feature.inventory.dto.FinishedPutAwayResponse;
import com.hbsisms.feature.production.dto.ProductionLeftoverInput;
import com.hbsisms.feature.production.dto.ProductionLeftoverResponse;
import com.hbsisms.feature.production.dto.ProductionOrderCompleteRequest;
import com.hbsisms.feature.production.dto.ProductionOrderCreateRequest;
import com.hbsisms.feature.production.dto.ProductionOrderMaterialLineResponse;
import com.hbsisms.feature.production.dto.ProductionOrderResponse;
import com.hbsisms.feature.production.dto.ProductionOrderUpdateRequest;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.BusinessCodeService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductionOrderService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ProductionOrderRepository productionOrderRepository;
    private final ProductionOrderMaterialLineRepository materialLineRepository;
    private final ProductionLeftoverRepository leftoverRepository;
    private final MaterialIssueLineRepository materialIssueLineRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final BomExplosionService bomExplosionService;
    private final MaterialIssueService materialIssueService;
    private final GoodsReceiptService goodsReceiptService;
    private final RawMaterialStockService rawMaterialStockService;
    private final BusinessCodeService businessCodeService;
    private final AppConfigService appConfigService;
    private final UserRepository userRepository;
    private final Clock clock;

    public ProductionOrderService(
            ProductionOrderRepository productionOrderRepository,
            ProductionOrderMaterialLineRepository materialLineRepository,
            ProductionLeftoverRepository leftoverRepository,
            MaterialIssueLineRepository materialIssueLineRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            BomExplosionService bomExplosionService,
            MaterialIssueService materialIssueService,
            GoodsReceiptService goodsReceiptService,
            RawMaterialStockService rawMaterialStockService,
            BusinessCodeService businessCodeService,
            AppConfigService appConfigService,
            UserRepository userRepository,
            Clock clock) {
        this.productionOrderRepository = productionOrderRepository;
        this.materialLineRepository = materialLineRepository;
        this.leftoverRepository = leftoverRepository;
        this.materialIssueLineRepository = materialIssueLineRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.bomExplosionService = bomExplosionService;
        this.materialIssueService = materialIssueService;
        this.goodsReceiptService = goodsReceiptService;
        this.rawMaterialStockService = rawMaterialStockService;
        this.businessCodeService = businessCodeService;
        this.appConfigService = appConfigService;
        this.userRepository = userRepository;
        this.clock = clock;
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
        requireWholeTrays(request.plannedQty());

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
        requireWholeTrays(request.plannedQty());

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
     * Task 51: báo hoàn thành SX + leftover -TL + sinh phiếu nhập TP Pending.
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

        LocalDate today = LocalDate.now(clock.withZone(VN));
        order.setFinishedDate(request.finishedDate() != null ? request.finishedDate() : today);
        applyDefect(order, request);

        ProductVariant variant = resolveFinishedVariant(order, request.variantId());
        order.setActualQty(request.actualQty());
        order.setActualProductionCost(
                request.actualProductionCost() == null ? BigDecimal.ZERO : request.actualProductionCost());
        order.setFinishedVariant(variant);

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        applyLeftovers(order, issue, request.leftovers(), actor, today);

        GoodsReceipt receipt = goodsReceiptService.createPendingFinishedFromProduction(
                order.getId(), order.getCode(), variant, request.actualQty(), actorId);
        order.setFinishedReceipt(receipt);
        order.setStatus(ProductionOrderStatus.AWAITING_RECEIPT);
        productionOrderRepository.save(order);
        return toResponse(requireOrder(id));
    }

    private void applyDefect(ProductionOrder order, ProductionOrderCompleteRequest request) {
        BigDecimal defectQty = request.defectQty();
        if (defectQty == null || defectQty.signum() <= 0) {
            order.setDefectQty(null);
            order.setDefectReason(null);
            return;
        }
        String reason = Strings.trimToNull(request.defectReason());
        if (reason == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do hàng hỏng");
        }
        order.setDefectQty(defectQty);
        order.setDefectReason(reason);
    }

    /**
     * Mỗi dòng leftover, trừ chi phí NVL thực tế: đồ khu lạnh → lô {@code <lô gốc>-TL} (trùng thì -TL2, -TL3…);
     * đồ khu khô → cộng lại vào lô đã xuất.
     * Source lot = dòng xuất sau cùng của NVL trên phiếu (khớp FE {@code lots[last]}).
     */
    private void applyLeftovers(
            ProductionOrder order,
            MaterialIssue issue,
            List<ProductionLeftoverInput> leftovers,
            AppUser actor,
            LocalDate today) {
        if (leftovers == null || leftovers.isEmpty()) {
            return;
        }
        List<MaterialIssueLine> issuedLines =
                materialIssueLineRepository.findByMaterialIssue_IdOrderBySortOrderAscIdAsc(issue.getId());
        Map<Long, IssuedMaterialAgg> byMaterial = aggregateIssued(issuedLines);

        Map<Long, BigDecimal> leftoverRequested = new HashMap<>();
        /** Số đã trả về theo từng dòng xuất (đồ khu khô) — nhiều dòng dư cùng NVL không trả quá số đã xuất. */
        Map<Long, BigDecimal> returnedByLine = new HashMap<>();
        List<ProductionLeftover> saved = new ArrayList<>();
        for (ProductionLeftoverInput input : leftovers) {
            if (input == null || input.qty() == null || input.qty().signum() <= 0) {
                continue;
            }
            Long materialId = input.rawMaterialId();
            IssuedMaterialAgg agg = byMaterial.get(materialId);
            if (agg == null) {
                throw ApiException.badRequest("NVL dư không khớp phiếu xuất của lệnh");
            }
            BigDecimal already = leftoverRequested.getOrDefault(materialId, BigDecimal.ZERO);
            BigDecimal nextTotal = already.add(input.qty());
            if (nextTotal.compareTo(agg.totalIssued) > 0) {
                throw ApiException.badRequest(
                        "Số lượng dư " + agg.material.getName() + " vượt tổng đã xuất (" + agg.totalIssued + ")");
            }
            leftoverRequested.put(materialId, nextTotal);

            String note = "Trả lại NVL dư lệnh " + order.getCode();
            if (RawMaterialStockService.isColdStorage(agg.material)) {
                saved.add(leftoverToNewLot(order, agg, input, actor, today, note));
            } else {
                saved.addAll(leftoverToSourceLots(order, agg, input.qty(), returnedByLine, actor, note));
            }
        }
        if (!saved.isEmpty()) {
            leftoverRepository.saveAll(saved);
        }
    }

    /**
     * Đồ khu lạnh (đã ra khỏi kho lạnh, có thể đã rã đông): tách lô {@code <lô gốc>-TL} (trùng → -TL2…), hạn do kho chọn,
     * không trước hôm nay và không muộn hơn hạn lô đã xuất; giá theo lô xuất sau cùng.
     */
    private ProductionLeftover leftoverToNewLot(
            ProductionOrder order, IssuedMaterialAgg agg, ProductionLeftoverInput input, AppUser actor, LocalDate today, String note) {
        if (input.expDate() == null) {
            throw ApiException.badRequest("Vui lòng nhập hạn dùng lô dư");
        }
        if (input.expDate().isBefore(today)) {
            throw ApiException.badRequest("Hạn dùng lô dư không được ở quá khứ");
        }
        if (agg.minExpDate != null && input.expDate().isAfter(agg.minExpDate)) {
            throw ApiException.badRequest(
                    "Hạn dùng lô dư không được muộn hơn hạn lô đã xuất (" + agg.minExpDate + ")");
        }
        RawMaterialLot sourceLot = agg.lastLot;
        BigDecimal unitPrice = sourceLot.getUnitPrice() == null ? BigDecimal.ZERO : sourceLot.getUnitPrice();
        // Gốc-TL, đã có thì Gốc-TL2, TL3…; lô xuất ra là lô -TL thì vẫn lấy mã gốc (không thành -TL-TL).
        String lotCode = rawMaterialStockService.nextLeftoverLotCode(agg.material, sourceLot.getLotCode());
        RawMaterialLot returned = rawMaterialStockService.receive(
                agg.material, lotCode, null, input.expDate(), unitPrice, input.qty(), null, actor, note);
        return leftoverRow(order, agg.material, returned, input.qty(), input.expDate(), unitPrice);
    }

    /**
     * Đồ khu khô (khay, màng bọc, gia vị…): cộng lại vào chính lô đã xuất, giữ mã lô / HSD / đơn giá. Trả ngược từ dòng xuất
     * sau cùng, mỗi dòng không quá số đã xuất từ dòng đó → số và tiền từng lô luôn khớp.
     */
    private List<ProductionLeftover> leftoverToSourceLots(
            ProductionOrder order,
            IssuedMaterialAgg agg,
            BigDecimal qty,
            Map<Long, BigDecimal> returnedByLine,
            AppUser actor,
            String note) {
        List<ProductionLeftover> rows = new ArrayList<>();
        BigDecimal left = qty;
        for (int i = agg.lines.size() - 1; i >= 0 && left.signum() > 0; i--) {
            MaterialIssueLine line = agg.lines.get(i);
            BigDecimal room = line.getQtyIssued().subtract(returnedByLine.getOrDefault(line.getId(), BigDecimal.ZERO));
            BigDecimal back = left.min(room);
            if (back.signum() <= 0) {
                continue;
            }
            RawMaterialLot lot = rawMaterialStockService.returnToLot(
                    line.getRawMaterialLot().getId(),
                    back,
                    RawMaterialStockService.REF_PRODUCTION_ORDER,
                    order.getId(),
                    actor,
                    note);
            returnedByLine.merge(line.getId(), back, BigDecimal::add);
            BigDecimal unitPrice = lot.getUnitPrice() == null ? BigDecimal.ZERO : lot.getUnitPrice();
            rows.add(leftoverRow(order, agg.material, lot, back, lot.getExpDate(), unitPrice));
            left = left.subtract(back);
        }
        return rows;
    }

    /** Một dòng NVL dư + trừ tiền phần dư khỏi chi phí NVL thực tế của lệnh. */
    private static ProductionLeftover leftoverRow(
            ProductionOrder order, RawMaterial material, RawMaterialLot lot, BigDecimal qty, LocalDate expDate, BigDecimal unitPrice) {
        BigDecimal amount = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = order.getActualMaterialCost() == null ? BigDecimal.ZERO : order.getActualMaterialCost();
        BigDecimal credited = cost.subtract(amount);
        order.setActualMaterialCost(credited.signum() < 0 ? BigDecimal.ZERO : credited);

        ProductionLeftover row = new ProductionLeftover();
        row.setProductionOrder(order);
        row.setRawMaterial(material);
        row.setLot(lot);
        row.setQty(qty);
        row.setExpDate(expDate);
        row.setAmount(amount);
        return row;
    }

    private static Map<Long, IssuedMaterialAgg> aggregateIssued(List<MaterialIssueLine> lines) {
        Map<Long, IssuedMaterialAgg> map = new HashMap<>();
        for (MaterialIssueLine line : lines) {
            if (line.getRawMaterialLot() == null
                    || line.getQtyIssued() == null
                    || line.getQtyIssued().signum() <= 0) {
                continue;
            }
            Long materialId = line.getRawMaterial().getId();
            IssuedMaterialAgg agg = map.computeIfAbsent(
                    materialId, id -> new IssuedMaterialAgg(line.getRawMaterial()));
            agg.totalIssued = agg.totalIssued.add(line.getQtyIssued());
            agg.lastLot = line.getRawMaterialLot();
            agg.lines.add(line);
            LocalDate exp = line.getRawMaterialLot().getExpDate();
            if (exp != null) {
                agg.minExpDate = agg.minExpDate == null || exp.isBefore(agg.minExpDate) ? exp : agg.minExpDate;
            }
        }
        return map;
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
        // BOM chuẩn khay 1 kg — ưu tiên quy cách 1 kg khi không chọn variantId.
        List<ProductVariant> active = productVariantRepository
                .findByProduct_IdOrderByIdAsc(order.getProduct().getId())
                .stream()
                .filter(ProductVariant::isActive)
                .toList();
        return active.stream()
                .filter(PackSizes::isOneKg)
                .findFirst()
                .or(() -> active.stream().findFirst())
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
        List<ProductionLeftoverResponse> leftovers = leftoverRepository
                .findByProductionOrder_IdOrderByIdAsc(order.getId())
                .stream()
                .map(ProductionLeftoverResponse::from)
                .toList();
        BigDecimal unitCost = null;
        Integer markupPercent = null;
        BigDecimal suggestedSellPrice = null;
        FinishedPutAwayResponse putAway = null;
        if (order.getStatus() == ProductionOrderStatus.COMPLETED) {
            GoodsReceipt finished = order.getFinishedReceipt();
            putAway = finished == null ? null : goodsReceiptService.finishedPutAway(finished.getId());
            BigDecimal qty = order.getActualQty();
            BigDecimal material =
                    order.getActualMaterialCost() == null ? BigDecimal.ZERO : order.getActualMaterialCost();
            BigDecimal production =
                    order.getActualProductionCost() == null ? BigDecimal.ZERO : order.getActualProductionCost();
            if (qty != null && qty.signum() > 0) {
                unitCost = material.add(production).divide(qty, 0, RoundingMode.HALF_UP);
                markupPercent = appConfigService.sellPriceMarkupPercent();
                suggestedSellPrice = appConfigService.sellPriceFromUnitCost(unitCost);
            }
        }
        return ProductionOrderResponse.from(
                order, materials, leftovers, unitCost, markupPercent, suggestedSellPrice, null, putAway);
    }

    /** Lệnh sản xuất tính theo khay — không có 1,5 khay. */
    private static void requireWholeTrays(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0 || qty.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest("Số lượng kế hoạch phải là số khay nguyên lớn hơn 0");
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static final class IssuedMaterialAgg {
        private final RawMaterial material;
        private BigDecimal totalIssued = BigDecimal.ZERO;
        private RawMaterialLot lastLot;
        private LocalDate minExpDate;
        /** Các dòng xuất của NVL theo thứ tự xuất (đồ khu khô trả ngược từ dòng cuối). */
        private final List<MaterialIssueLine> lines = new ArrayList<>();

        private IssuedMaterialAgg(RawMaterial material) {
            this.material = material;
        }
    }
}
