package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.PackSizes;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.FinishedPutAwayResponse;
import com.hbsisms.feature.inventory.dto.FinishedReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.FinishedReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteLineRequest;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.RawReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.ReceiptItemResponse;
import com.hbsisms.feature.inventory.dto.ReceiptLineResponse;
import com.hbsisms.feature.production.ProductionOrder;
import com.hbsisms.feature.production.ProductionOrderRepository;
import com.hbsisms.feature.production.ProductionOrderStatus;
import com.hbsisms.feature.sales.OrderAllocationService;
import com.hbsisms.feature.sales.PreorderFulfillmentService;
import com.hbsisms.feature.system.AppConfigService;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phiếu nhập kho.
 * <ul>
 *   <li>Thành phẩm tay: nhập ngay (cộng tồn theo lô + vị trí), phân bổ cho đơn đang thiếu hàng.</li>
 *   <li>Thành phẩm từ lệnh SX (dòng 52): phiếu Pending → thủ kho xác nhận lô/NSX/HSD/vị trí/SL → cộng tồn,
 *       lưu giá vốn/khay, đóng lệnh {@code COMPLETED}, cập nhật giá niêm yết quy cách =
 *       giá vốn × (1 + sell_price_markup_percent/100).</li>
 *   <li>Nguyên vật liệu (Inter 1 dòng 46–47): tạo phiếu ở trạng thái Chờ nhập — chưa cộng tồn; hàng về thì
 *       thủ kho xác nhận SL thực nhận, lô, NSX/HSD, đơn giá → cộng tồn theo lô NVL, phiếu Hoàn thành.
 *       Phiếu chờ có thể hủy.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class GoodsReceiptService {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptService.class);

    public static final String REF_TYPE = "goods_receipt";

    /** Theo mã phiếu: mã sinh tăng dần theo id nên sắp theo id = sắp theo mã (không lệch khi số mã vượt 4 chữ số). */
    private static final Sort LIST_SORT_NEWEST = Sort.by(Sort.Direction.DESC, "id");
    private static final Sort LIST_SORT_OLDEST = Sort.by(Sort.Direction.ASC, "id");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Matches create-time summary of legacy raw receipts: {@code Name: qty unit} joined by {@code ; }. */
    private static final Pattern RAW_ITEM_SEGMENT =
            Pattern.compile("^(.+):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(.*)$");

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final RawMaterialStockService rawMaterialStockService;
    private final StockService stockService;
    private final ProductionOrderRepository productionOrderRepository;
    private final UserRepository userRepository;
    private final OrderAllocationService orderAllocationService;
    private final PreorderFulfillmentService preorderFulfillmentService;
    private final AppConfigService appConfigService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public GoodsReceiptService(
            GoodsReceiptRepository goodsReceiptRepository,
            GoodsReceiptLineRepository goodsReceiptLineRepository,
            StockLedgerRepository stockLedgerRepository,
            ProductVariantRepository productVariantRepository,
            LocationRepository locationRepository,
            LotRepository lotRepository,
            RawMaterialRepository rawMaterialRepository,
            RawMaterialStockService rawMaterialStockService,
            StockService stockService,
            ProductionOrderRepository productionOrderRepository,
            UserRepository userRepository,
            OrderAllocationService orderAllocationService,
            PreorderFulfillmentService preorderFulfillmentService,
            AppConfigService appConfigService,
            AuditLogService auditLogService,
            Clock clock) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.goodsReceiptLineRepository = goodsReceiptLineRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.productVariantRepository = productVariantRepository;
        this.locationRepository = locationRepository;
        this.lotRepository = lotRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.rawMaterialStockService = rawMaterialStockService;
        this.stockService = stockService;
        this.productionOrderRepository = productionOrderRepository;
        this.userRepository = userRepository;
        this.orderAllocationService = orderAllocationService;
        this.preorderFulfillmentService = preorderFulfillmentService;
        this.appConfigService = appConfigService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Đọc loại phiếu (không khóa) — controller chọn DTO complete RAW vs FINISHED. */
    public ReceiptType requireType(Long id) {
        GoodsReceipt receipt = goodsReceiptRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
        return receipt.getReceiptType();
    }

    public PageResponse<GoodsReceiptResponse> list(String q, String receiptType, int page, int pageSize) {
        return list(q, receiptType, null, null, page, pageSize);
    }

    public PageResponse<GoodsReceiptResponse> list(
            String q, String receiptType, String status, int page, int pageSize) {
        return list(q, receiptType, status, null, page, pageSize);
    }

    /** @param sort "asc" = mã phiếu tăng dần (cũ nhất trước); còn lại (mặc định) = mã giảm dần (mới nhất trước) */
    public PageResponse<GoodsReceiptResponse> list(
            String q, String receiptType, String status, String sort, int page, int pageSize) {
        Sort listSort = "asc".equalsIgnoreCase(sort == null ? "" : sort.trim()) ? LIST_SORT_OLDEST : LIST_SORT_NEWEST;
        ReceiptType typeFilter = parseReceiptType(receiptType);
        ReceiptStatus statusFilter = parseStatus(status);
        Specification<GoodsReceipt> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("receiptType"), typeFilter));
            }
            if (statusFilter == ReceiptStatus.COMPLETED) {
                // Phiếu tạo trước khi có trạng thái (status trống) = đã hoàn thành.
                predicates.add(cb.or(cb.equal(root.get("status"), statusFilter), cb.isNull(root.get("status"))));
            } else if (statusFilter != null) {
                predicates.add(cb.equal(root.get("status"), statusFilter));
            }
            String needle = Strings.trimToNull(q);
            if (needle != null) {
                String pattern = "%" + needle.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("source")), pattern)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<GoodsReceipt> receipts = goodsReceiptRepository.findAll(spec, PageRequests.of(page, pageSize, listSort));
        Map<Long, List<GoodsReceiptLine>> linesByReceipt = rawLinesOf(receipts.getContent());
        return PageResponse.from(receipts.map(
                receipt -> toResponse(receipt, linesByReceipt.getOrDefault(receipt.getId(), List.of()))));
    }

    /** Chi tiết phiếu (kèm các dòng NVL). */
    public GoodsReceiptResponse get(Long id) {
        GoodsReceipt receipt = goodsReceiptRepository
                .findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
        return toResponse(receipt, goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(id));
    }

    @Transactional
    public GoodsReceiptResponse create(GoodsReceiptRequest request, Long actorId) {
        String source = Strings.trimToNull(request.source());
        if (source == null) {
            throw ApiException.badRequest("Vui lòng nhập nguồn hàng");
        }

        AppUser actor = actorId == null
                ? null
                : userRepository
                        .findById(actorId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));

        Instant now = Instant.now(clock);
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setCode(nextPnCode());
        receipt.setReceiptType(request.receiptType());
        receipt.setSource(source);
        receipt.setNote(Strings.trimToNull(request.note()));
        receipt.setReceivedBy(actor);
        receipt.setReceivedAt(now);

        if (request.receiptType() == ReceiptType.FINISHED) {
            // Thành phẩm chỉ nhập qua lệnh sản xuất — phiếu nhập kho cửa hàng chỉ nhận nguyên liệu.
            throw ApiException.badRequest(
                    "Không tạo phiếu nhập thành phẩm tại đây. Thành phẩm nhập qua lệnh sản xuất.");
        }
        if (request.receiptType() == ReceiptType.RAW) {
            // Inter 1 dòng 46: phiếu NVL chờ nhập — chưa cộng tồn.
            receipt.setStatus(ReceiptStatus.PENDING);
            receipt.setExpectedDate(request.expectedDate());
            List<RawMaterial> materials = validateRawLines(request.rawLines());
            receipt = goodsReceiptRepository.saveAndFlush(receipt);
            List<GoodsReceiptLine> lines = new ArrayList<>();
            for (int i = 0; i < materials.size(); i++) {
                GoodsReceiptLine line = new GoodsReceiptLine();
                line.setReceipt(receipt);
                line.setRawMaterial(materials.get(i));
                line.setLineNo(i + 1);
                line.setQtyExpected(request.rawLines().get(i).qty());
                lines.add(line);
            }
            lines = goodsReceiptLineRepository.saveAll(lines);
            auditLogService.record(actorId, "create", REF_TYPE, receipt.getId(), Map.of(
                    "code", receipt.getCode(),
                    "receiptType", ReceiptType.RAW.code(),
                    "status", ReceiptStatus.PENDING.code(),
                    "lines", lines.size()));
            return toResponse(receipt, lines);
        }
        throw ApiException.badRequest("Loại phiếu không hỗ trợ");
    }

    /**
     * Task 51 (Cường): phiếu nhập thành phẩm Pending từ lệnh SX — chưa cộng tồn.
     * Long sẽ xác nhận nhập (lô / vị trí / giá vốn) ở bước sau.
     */
    @Transactional
    public GoodsReceipt createPendingFinishedFromProduction(
            Long productionOrderId,
            String productionOrderCode,
            ProductVariant variant,
            BigDecimal qty,
            Long actorId) {
        if (productionOrderId == null) {
            throw ApiException.badRequest("Thiếu lệnh sản xuất");
        }
        if (variant == null) {
            throw ApiException.badRequest("Thiếu quy cách thành phẩm");
        }
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Số lượng thành phẩm phải lớn hơn 0");
        }
        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        Instant now = Instant.now(clock);
        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setCode(nextPnCode());
        receipt.setReceiptType(ReceiptType.FINISHED);
        receipt.setStatus(ReceiptStatus.PENDING);
        receipt.setProductionOrderId(productionOrderId);
        receipt.setSource(productionOrderCode);
        receipt.setNote(
                "Chờ nhập TP từ lệnh "
                        + productionOrderCode
                        + " · "
                        + variant.getSku()
                        + " × "
                        + qty.stripTrailingZeros().toPlainString());
        receipt.setReceivedBy(actor);
        receipt.setReceivedAt(now);
        GoodsReceipt saved = goodsReceiptRepository.saveAndFlush(receipt);
        GoodsReceiptLine line = new GoodsReceiptLine();
        line.setReceipt(saved);
        line.setProductVariant(variant);
        line.setLineNo(1);
        line.setQtyExpected(qty);
        goodsReceiptLineRepository.save(line);
        auditLogService.record(actorId, "create", REF_TYPE, saved.getId(), Map.of(
                "code", saved.getCode(),
                "receiptType", ReceiptType.FINISHED.code(),
                "status", ReceiptStatus.PENDING.code(),
                "productionOrderId", productionOrderId,
                "variantId", variant.getId(),
                "qtyExpected", qty));
        return saved;
    }

    /**
     * Inter 1 dòng 47: thủ kho xác nhận nhập kho phiếu NVL — mỗi dòng nhập SL thực nhận (có thể khác dự kiến,
     * 0 = không về), mã lô (trống = tự sinh), NSX/HSD, đơn giá. Cộng tồn theo lô NVL, phiếu → Hoàn thành.
     * Khóa phiếu để 2 người xác nhận cùng lúc không cộng tồn 2 lần.
     */
    @Transactional
    public GoodsReceiptResponse complete(Long id, RawReceiptCompleteRequest request, Long actorId) {
        GoodsReceipt receipt = goodsReceiptRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
        if (receipt.getReceiptType() != ReceiptType.RAW) {
            throw ApiException.conflict("Chỉ phiếu nhập nguyên liệu mới xác nhận nhập kho theo bước này");
        }
        assertPending(receipt);

        List<GoodsReceiptLine> lines = goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(id);
        Map<Long, RawReceiptCompleteLineRequest> inputs = indexCompleteLines(request, lines);
        LocalDate today = LocalDate.now(clock.withZone(VN));
        BigDecimal totalReceived = BigDecimal.ZERO;
        for (GoodsReceiptLine line : lines) {
            RawReceiptCompleteLineRequest input = inputs.get(line.getId());
            String name = line.getRawMaterial().getName();
            if (input.qtyReceived().signum() > 0) {
                if (input.unitPrice() == null || input.unitPrice().signum() <= 0) {
                    throw ApiException.badRequest("Vui lòng nhập đơn giá nhập lớn hơn 0 cho " + name);
                }
                if (input.mfgDate() != null && input.expDate() != null && input.expDate().isBefore(input.mfgDate())) {
                    throw ApiException.badRequest("HSD của " + name + " không được trước ngày sản xuất");
                }
                if (input.expDate() != null && input.expDate().isBefore(today)) {
                    throw ApiException.badRequest("HSD của " + name + " đã qua - không nhập kho lô hết hạn");
                }
            }
            totalReceived = totalReceived.add(input.qtyReceived());
        }
        if (totalReceived.signum() <= 0) {
            throw ApiException.badRequest(
                    "Phải có ít nhất một dòng thực nhận lớn hơn 0 - nếu hàng không về, hãy hủy phiếu");
        }

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        // Khóa NVL theo thứ tự id để 2 phiếu cùng NVL không khóa chéo nhau.
        Map<Long, RawMaterial> locked = new HashMap<>();
        lines.stream()
                .map(line -> line.getRawMaterial().getId())
                .distinct()
                .sorted()
                .forEach(materialId -> locked.put(materialId, rawMaterialRepository
                        .findByIdForUpdate(materialId)
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"))));

        for (GoodsReceiptLine line : lines) {
            RawReceiptCompleteLineRequest input = inputs.get(line.getId());
            line.setQtyReceived(input.qtyReceived());
            line.setMfgDate(input.mfgDate());
            line.setExpDate(input.expDate());
            if (input.qtyReceived().signum() <= 0) {
                line.setLotCode(null);
                line.setUnitPrice(null);
                continue;
            }
            RawMaterialLot lot = rawMaterialStockService.receive(
                    locked.get(line.getRawMaterial().getId()),
                    input.lotCode(),
                    input.mfgDate(),
                    input.expDate(),
                    input.unitPrice(),
                    input.qtyReceived(),
                    receipt,
                    actor,
                    "Nhập nguyên liệu " + receipt.getCode());
            line.setLotCode(lot.getLotCode());
            line.setUnitPrice(input.unitPrice());
            line.setRawLot(lot);
        }
        goodsReceiptLineRepository.saveAll(lines);

        Instant now = Instant.now(clock);
        receipt.setStatus(ReceiptStatus.COMPLETED);
        receipt.setReceivedAt(now);
        receipt.setCompletedAt(now);
        receipt.setCompletedBy(actor);
        String note = Strings.trimToNull(request.note());
        if (note != null) {
            receipt.setNote(receipt.getNote() == null ? note : receipt.getNote() + " - " + note);
        }
        goodsReceiptRepository.save(receipt);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", receipt.getCode());
        detail.put("status", ReceiptStatus.COMPLETED.code());
        detail.put("lines", lines.size());
        detail.put("qtyReceived", totalReceived);
        auditLogService.record(actorId, "complete", REF_TYPE, receipt.getId(), detail);
        return toResponse(receipt, lines);
    }

    /**
     * Lô / HSD / vị trí của phiếu thành phẩm đã nhập kho (màn Hoàn thành của lệnh SX).
     * {@code null} khi phiếu chưa nhập. Vị trí đọc từ dòng sổ kho IN của phiếu.
     */
    public FinishedPutAwayResponse finishedPutAway(Long receiptId) {
        if (receiptId == null) {
            return null;
        }
        GoodsReceiptLine line = goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(receiptId).stream()
                .filter(l -> l.getProductVariant() != null
                        && l.getQtyReceived() != null
                        && l.getQtyReceived().signum() > 0)
                .findFirst()
                .orElse(null);
        if (line == null) {
            return null;
        }
        Location location = stockLedgerRepository.findByRefTypeAndRefIdForAllocation(REF_TYPE, receiptId).stream()
                .filter(e -> e.getEntryType() == LedgerEntryType.IN && e.getLocation() != null)
                .map(StockLedger::getLocation)
                .findFirst()
                .orElse(null);
        return new FinishedPutAwayResponse(
                line.getLotCode(),
                line.getExpDate(),
                location == null ? null : location.getCode(),
                location == null ? null : location.getZone(),
                location == null || location.getWarehouse() == null ? null : location.getWarehouse().getName());
    }

    /**
     * Inter 1 dòng 52: xác nhận nhập kho phiếu TP pending từ lệnh SX — lô / NSX / HSD / vị trí / SL,
     * giá vốn/khay = (NVL thật + chi phí SX) ÷ SL nhập, cộng tồn, phiếu + lệnh → Hoàn thành.
     */
    @Transactional
    public GoodsReceiptResponse completeFinished(Long id, FinishedReceiptCompleteRequest request, Long actorId) {
        try {
            return doCompleteFinished(id, request, actorId);
        } catch (ApiException ex) {
            throw ex;
        } catch (DataIntegrityViolationException ex) {
            String root = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
            log.warn("completeFinished integrity id={}: {}", id, root);
            if (root != null && root.toLowerCase(Locale.ROOT).contains("production_order_status")) {
                throw ApiException.conflict(
                        "DB chưa nhận trạng thái COMPLETED của lệnh SX — restart API rồi thử lại");
            }
            throw ApiException.conflict("Không nhập kho được: " + root);
        } catch (RuntimeException ex) {
            Throwable root = ex;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            log.error("completeFinished failed id={}", id, ex);
            throw ApiException.badRequest("Không nhập kho được: " + root.getMessage());
        }
    }

    private GoodsReceiptResponse doCompleteFinished(
            Long id, FinishedReceiptCompleteRequest request, Long actorId) {
        GoodsReceipt receipt = goodsReceiptRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
        if (receipt.getReceiptType() != ReceiptType.FINISHED) {
            throw ApiException.conflict("Chỉ phiếu nhập thành phẩm mới xác nhận theo bước này");
        }
        // Dùng field thô — getStatus() coi null là COMPLETED (phiếu cũ).
        if (receipt.getStatus() == ReceiptStatus.COMPLETED) {
            return toResponse(receipt, goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(id));
        }
        assertPending(receipt);
        if (receipt.getProductionOrderId() == null) {
            throw ApiException.conflict("Phiếu thành phẩm này không gắn lệnh sản xuất — hãy tạo phiếu nhập tay");
        }
        if (request.mfgDate() != null && request.expDate().isBefore(request.mfgDate())) {
            throw ApiException.badRequest("HSD không được trước ngày sản xuất");
        }
        LocalDate today = LocalDate.now(clock.withZone(VN));
        if (request.expDate().isBefore(today)) {
            throw ApiException.badRequest("HSD đã qua — không nhập kho lô hết hạn");
        }
        Location putAway = locationRepository
                .findById(request.locationId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
        String zone = putAway.getZone() == null ? "" : putAway.getZone().trim();
        String locCode = putAway.getCode() == null ? "" : putAway.getCode();
        if ("Ngừng dùng".equalsIgnoreCase(zone) || locCode.toUpperCase(Locale.ROOT).startsWith("XX-")) {
            throw ApiException.badRequest("Vị trí đã ngừng dùng — chọn vị trí kho thường (vd. A-01)");
        }
        if (putAway.getWarehouse() != null && !putAway.getWarehouse().isActive()) {
            throw ApiException.badRequest("Kho của vị trí đã ngừng dùng");
        }

        List<GoodsReceiptLine> lines = goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(id);
        GoodsReceiptLine line = lines.stream()
                .filter(l -> l.getProductVariant() != null)
                .findFirst()
                .orElseThrow(() -> ApiException.badRequest("Phiếu thiếu dòng thành phẩm"));
        Long variantId = line.getProductVariant().getId();
        ProductVariant variant = productVariantRepository
                .findWithProductById(variantId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));

        // Chỉ đọc chi phí/mã lệnh — không findByIdForUpdate (tránh conflict OneToOne finishedReceipt
        // trùng phiếu đang khóa → Hibernate entityEntry is null).
        ProductionOrder order = productionOrderRepository
                .findById(receipt.getProductionOrderId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lệnh sản xuất"));
        if (order.getStatus() == ProductionOrderStatus.COMPLETED) {
            return toResponse(receipt, lines);
        }
        if (order.getStatus() != ProductionOrderStatus.AWAITING_RECEIPT) {
            throw ApiException.conflict("Lệnh sản xuất không ở trạng thái chờ nhập kho");
        }
        final Long linkedSalesOrderIdEarly =
                order.getSalesOrder() == null ? null : order.getSalesOrder().getId();

        BigDecimal material = order.getActualMaterialCost() == null ? BigDecimal.ZERO : order.getActualMaterialCost();
        BigDecimal production =
                order.getActualProductionCost() == null ? BigDecimal.ZERO : order.getActualProductionCost();
        BigDecimal unitCost = material
                .add(production)
                .divide(BigDecimal.valueOf(request.qty()), 2, RoundingMode.HALF_UP);
        // Giá bán gợi ý (Inter 1 #53, Inter 5 #6): giá vốn / khay quy về 1 kg (khay 0,5 kg: × 2) rồi cộng lời.
        // Chỉ ghi vào nhật ký + hiện ở màn hoàn thành lệnh — KHÔNG ghi vào giá niêm yết (kể cả khay 0 ₫);
        // áp dụng giá gợi ý do Chủ quyết (Inter 5 #6).
        int markupPercent = appConfigService.sellPriceMarkupPercent();
        BigDecimal costOneKg = unitCost.divide(PackSizes.weightKg(variant), 2, RoundingMode.HALF_UP);
        BigDecimal priceOneKg = appConfigService.sellPriceFromUnitCost(costOneKg);

        String orderCode = order.getCode();
        LocalDate finishedDate = order.getFinishedDate();
        Long orderId = order.getId();

        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        FinishedReceiptLineRequest stockLine = new FinishedReceiptLineRequest(
                variant.getId(),
                request.lotCode(),
                request.mfgDate() != null ? request.mfgDate() : finishedDate,
                request.expDate(),
                request.locationId(),
                request.qty());
        Lot lot = resolveLot(variant, stockLine);
        lot.setUnitCost(unitCost);
        lotRepository.save(lot);
        stockService.receiveIn(StockMutation.of(
                variant.getId(),
                lot.getId(),
                request.locationId(),
                request.qty(),
                REF_TYPE,
                receipt.getId(),
                "Nhập thành phẩm từ lệnh " + orderCode,
                actorId));

        line.setQtyReceived(BigDecimal.valueOf(request.qty()));
        line.setLotCode(lot.getLotCode());
        line.setMfgDate(stockLine.mfgDate());
        line.setExpDate(request.expDate());
        line.setUnitPrice(unitCost);
        goodsReceiptLineRepository.save(line);

        Instant now = Instant.now(clock);
        receipt.setStatus(ReceiptStatus.COMPLETED);
        receipt.setReceivedAt(now);
        receipt.setCompletedAt(now);
        receipt.setCompletedBy(actor);
        String note = Strings.trimToNull(request.note());
        if (note != null) {
            receipt.setNote(receipt.getNote() == null ? note : receipt.getNote() + " - " + note);
        }
        goodsReceiptRepository.save(receipt);

        int closed = productionOrderRepository.markCompletedAfterFinishedReceipt(orderId);
        if (closed == 0) {
            throw ApiException.conflict("Lệnh sản xuất không còn ở trạng thái chờ nhập kho");
        }

        // Phân bổ đơn outstanding sau commit — lỗi bước này không được rollback phiếu nhập TP.
        final Long allocateActorId = actorId;
        final String receiptCode = receipt.getCode();
        final Long linkedSalesOrderId = linkedSalesOrderIdEarly;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        orderAllocationService.allocateOutstandingAfterReceipt(allocateActorId);
                    } catch (RuntimeException ex) {
                        log.warn(
                                "allocateOutstandingAfterReceipt after {} failed: {}",
                                receiptCode,
                                ex.getMessage());
                    }
                    if (linkedSalesOrderId != null) {
                        try {
                            preorderFulfillmentService.tryAdvanceToSettlement(linkedSalesOrderId);
                        } catch (RuntimeException ex) {
                            log.warn(
                                    "tryAdvanceToSettlement order {} after {} failed: {}",
                                    linkedSalesOrderId,
                                    receiptCode,
                                    ex.getMessage());
                        }
                    }
                }
            });
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", receipt.getCode());
        detail.put("status", ReceiptStatus.COMPLETED.code());
        detail.put("productionOrderId", orderId);
        detail.put("qty", request.qty());
        detail.put("unitCost", unitCost);
        detail.put("suggestedSellPrice", priceOneKg);
        detail.put("markupPercent", markupPercent);
        detail.put("locationId", request.locationId());
        auditLogService.record(actorId, "complete", REF_TYPE, receipt.getId(), detail);

        // Không gọi GoodsReceiptResponse.from(receipt) — tránh lazy receivedBy/completedBy
        // (Hibernate entityEntry null khi phiếu/lệnh OneToOne vừa flush).
        String source = receipt.getSource();
        return new GoodsReceiptResponse(
                receipt.getId(),
                receipt.getCode(),
                ReceiptType.FINISHED,
                source,
                actorId,
                now,
                receipt.getNote(),
                actor == null ? null : actor.getFullName(),
                source == null || source.isBlank() ? "—" : source,
                List.of(new ReceiptItemResponse(
                        variant.getSku() + " · " + lot.getLotCode(),
                        request.qty().doubleValue(),
                        "khay")),
                ReceiptStatus.COMPLETED,
                null,
                now,
                actor == null ? null : actor.getFullName(),
                unitCost.multiply(BigDecimal.valueOf(request.qty())),
                List.of());
    }

    /** Hủy phiếu còn chờ nhập (chưa cộng tồn nên không phải hoàn tồn). */
    @Transactional
    public GoodsReceiptResponse cancel(Long id, String reason, Long actorId) {
        GoodsReceipt receipt = goodsReceiptRepository
                .findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập"));
        assertPending(receipt);
        AppUser actor = actorId == null ? null : userRepository.findById(actorId).orElse(null);
        String why = Strings.trimToNull(reason);
        receipt.setStatus(ReceiptStatus.CANCELLED);
        receipt.setCompletedAt(Instant.now(clock));
        receipt.setCompletedBy(actor);
        if (why != null) {
            String cancelNote = "Hủy: " + why;
            receipt.setNote(receipt.getNote() == null ? cancelNote : receipt.getNote() + " - " + cancelNote);
        }
        goodsReceiptRepository.save(receipt);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("code", receipt.getCode());
        detail.put("status", ReceiptStatus.CANCELLED.code());
        if (why != null) {
            detail.put("reason", why);
        }
        auditLogService.record(actorId, "cancel", REF_TYPE, receipt.getId(), detail);
        return toResponse(receipt, goodsReceiptLineRepository.findByReceipt_IdOrderByLineNoAsc(id));
    }

    private static void assertPending(GoodsReceipt receipt) {
        if (receipt.getStatus() == ReceiptStatus.COMPLETED) {
            throw ApiException.conflict("Phiếu " + receipt.getCode() + " đã nhập kho");
        }
        if (receipt.getStatus() == ReceiptStatus.CANCELLED) {
            throw ApiException.conflict("Phiếu " + receipt.getCode() + " đã hủy");
        }
    }

    /** Mỗi dòng phiếu phải có đúng 1 dòng xác nhận, không thừa, không trùng. */
    private static Map<Long, RawReceiptCompleteLineRequest> indexCompleteLines(
            RawReceiptCompleteRequest request, List<GoodsReceiptLine> lines) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw ApiException.badRequest("Phiếu phải có ít nhất một dòng");
        }
        Set<Long> lineIds = lines.stream().map(GoodsReceiptLine::getId).collect(Collectors.toSet());
        Map<Long, RawReceiptCompleteLineRequest> inputs = new HashMap<>();
        for (RawReceiptCompleteLineRequest input : request.lines()) {
            if (input == null || input.lineId() == null || !lineIds.contains(input.lineId())) {
                throw ApiException.badRequest("Dòng không thuộc phiếu này");
            }
            if (input.qtyReceived() == null || input.qtyReceived().signum() < 0) {
                throw ApiException.badRequest("Số lượng thực nhận không được âm");
            }
            if (inputs.put(input.lineId(), input) != null) {
                throw ApiException.badRequest("Dòng phiếu bị lặp");
            }
        }
        for (GoodsReceiptLine line : lines) {
            if (!inputs.containsKey(line.getId())) {
                throw ApiException.badRequest("Thiếu số lượng thực nhận cho " + line.getRawMaterial().getName());
            }
        }
        return inputs;
    }

    private List<RawMaterial> validateRawLines(List<RawReceiptLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest("Phiếu phải có ít nhất một dòng");
        }
        Set<Long> seen = new HashSet<>();
        List<RawMaterial> materials = new ArrayList<>();
        for (RawReceiptLineRequest line : lines) {
            if (line == null || line.rawMaterialId() == null) {
                throw ApiException.badRequest("Nguyên liệu là bắt buộc");
            }
            if (line.qty() == null || line.qty().signum() <= 0) {
                throw ApiException.badRequest("Số lượng dự kiến phải lớn hơn 0");
            }
            if (!seen.add(line.rawMaterialId())) {
                throw ApiException.badRequest("Mỗi nguyên liệu chỉ nhập một dòng trên phiếu");
            }
            RawMaterial material = rawMaterialRepository
                    .findById(line.rawMaterialId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
            if (!material.isActive()) {
                throw ApiException.conflict(material.getName() + " đã ngừng dùng");
            }
            materials.add(material);
        }
        return materials;
    }

    private List<ReceiptItemResponse> receiveFinished(
            GoodsReceipt receipt, List<FinishedReceiptLineRequest> lines, Long actorId) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest("Phiếu phải có ít nhất một dòng");
        }
        List<ReceiptItemResponse> items = new ArrayList<>();
        for (FinishedReceiptLineRequest line : lines) {
            if (line.mfgDate() != null && line.expDate().isBefore(line.mfgDate())) {
                throw ApiException.badRequest("HSD không được trước ngày sản xuất");
            }
            ProductVariant variant = productVariantRepository
                    .findWithProductById(line.variantId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));
            locationRepository
                    .findById(line.locationId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));

            Lot lot = resolveLot(variant, line);
            stockService.receiveIn(StockMutation.of(
                    variant.getId(),
                    lot.getId(),
                    line.locationId(),
                    line.qty(),
                    REF_TYPE,
                    receipt.getId(),
                    "Nhập thành phẩm",
                    actorId));
            items.add(new ReceiptItemResponse(
                    variant.getSku() + " · " + lot.getLotCode(), line.qty(), "khay"));
        }
        return items;
    }

    private Lot resolveLot(ProductVariant variant, FinishedReceiptLineRequest line) {
        String lotCode = Strings.trimToNull(line.lotCode());
        if (lotCode == null) {
            lotCode = autoLotCode();
        }
        String finalCode = lotCode;
        return lotRepository
                .findByLotCodeIgnoreCase(finalCode)
                .map(existing -> {
                    if (!existing.getVariant().getId().equals(variant.getId())) {
                        throw ApiException.conflict("Mã lô " + finalCode + " đã thuộc quy cách khác");
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    Lot lot = new Lot();
                    lot.setLotCode(finalCode);
                    lot.setVariant(variant);
                    lot.setMfgDate(line.mfgDate());
                    lot.setExpDate(line.expDate());
                    lot.setReceivedAt(Instant.now(clock));
                    return lotRepository.saveAndFlush(lot);
                });
    }

    private Map<Long, List<GoodsReceiptLine>> rawLinesOf(List<GoodsReceipt> receipts) {
        List<Long> ids = receipts.stream().map(GoodsReceipt::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return goodsReceiptLineRepository.findByReceipt_IdInOrderByLineNoAsc(ids).stream()
                .collect(Collectors.groupingBy(line -> line.getReceipt().getId()));
    }

    private GoodsReceiptResponse toResponse(GoodsReceipt receipt, List<GoodsReceiptLine> rawLines) {
        if (receipt.getReceiptType() == ReceiptType.FINISHED) {
            List<ReceiptItemResponse> ledgerItems = stockLedgerRepository
                    .findByRefTypeAndRefIdWithDetails(REF_TYPE, receipt.getId())
                    .stream()
                    .map(e -> {
                        String sku = e.getVariant().getSku();
                        String lotCode = e.getLot() == null ? "?" : e.getLot().getLotCode();
                        return new ReceiptItemResponse(sku + " · " + lotCode, e.getQtyDelta(), "khay");
                    })
                    .toList();
            if (!ledgerItems.isEmpty()) {
                return GoodsReceiptResponse.from(receipt, ledgerItems);
            }
            // PN TP pending từ SX: dòng variant + qtyExpected (ReceiptPanel prefill).
            List<GoodsReceiptLine> fgLines = rawLines.stream()
                    .filter(line -> line.getProductVariant() != null)
                    .sorted(Comparator.comparingInt(GoodsReceiptLine::getLineNo))
                    .toList();
            if (!fgLines.isEmpty()) {
                List<ReceiptItemResponse> items = fgLines.stream()
                        .map(line -> new ReceiptItemResponse(
                                line.getProductVariant().getSku(),
                                line.getQtyExpected().doubleValue(),
                                "khay"))
                        .toList();
                return GoodsReceiptResponse.from(receipt, items);
            }
            return GoodsReceiptResponse.from(receipt, List.of());
        }
        List<GoodsReceiptLine> nvlLines = rawLines.stream()
                .filter(line -> line.getRawMaterial() != null)
                .toList();
        if (nvlLines.isEmpty()) {
            // Phiếu NVL cũ (trước dòng 46): dòng chỉ lưu dạng tóm tắt trong ghi chú.
            return GoodsReceiptResponse.from(receipt, parseRawItemsFromNote(receipt.getNote()));
        }
        List<GoodsReceiptLine> sorted = nvlLines.stream()
                .sorted(Comparator.comparingInt(GoodsReceiptLine::getLineNo))
                .toList();
        boolean received = receipt.getStatus() == ReceiptStatus.COMPLETED;
        List<ReceiptItemResponse> items = sorted.stream()
                .map(line -> new ReceiptItemResponse(
                        line.getRawMaterial().getName(),
                        (received && line.getQtyReceived() != null ? line.getQtyReceived() : line.getQtyExpected())
                                .doubleValue(),
                        line.getRawMaterial().getUnit()))
                .toList();
        List<ReceiptLineResponse> lines = sorted.stream().map(ReceiptLineResponse::from).toList();
        return GoodsReceiptResponse.from(receipt, items, lines);
    }

    /**
     * Rebuilds list {@code items} from the note summary written at create time
     * ({@code [userNote — ]name: qty unit; ...}) — chỉ còn dùng cho phiếu NVL tạo trước dòng 46.
     */
    static List<ReceiptItemResponse> parseRawItemsFromNote(String note) {
        String raw = Strings.trimToNull(note);
        if (raw == null) {
            return List.of();
        }
        String summary = raw;
        int sep = raw.lastIndexOf(" — ");
        if (sep >= 0) {
            String after = raw.substring(sep + 3).trim();
            if (!after.isEmpty() && looksLikeRawItemSummary(after)) {
                summary = after;
            }
        }
        if (!looksLikeRawItemSummary(summary)) {
            return List.of();
        }
        List<ReceiptItemResponse> items = new ArrayList<>();
        for (String segment : summary.split(";")) {
            String part = segment.trim();
            if (part.isEmpty()) {
                continue;
            }
            Matcher m = RAW_ITEM_SEGMENT.matcher(part);
            if (!m.matches()) {
                continue;
            }
            items.add(new ReceiptItemResponse(
                    m.group(1).trim(), Double.parseDouble(m.group(2)), m.group(3).trim()));
        }
        return items;
    }

    private static boolean looksLikeRawItemSummary(String text) {
        String first = text.split(";", 2)[0].trim();
        return RAW_ITEM_SEGMENT.matcher(first).matches();
    }

    private String nextPnCode() {
        int next = goodsReceiptRepository
                .findMaxPnCode()
                .map(code -> {
                    String digits = code.replaceAll("\\D+", "");
                    try {
                        return Integer.parseInt(digits) + 1;
                    } catch (NumberFormatException ex) {
                        return 1;
                    }
                })
                .orElse(1);
        return "PN-" + String.format(Locale.ROOT, "%04d", next);
    }

    private String autoLotCode() {
        String day = LocalDate.now(clock.withZone(ZoneOffset.UTC)).format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 1; i <= 99; i++) {
            String candidate = "LOT-" + day.substring(2) + "-" + String.format(Locale.ROOT, "%02d", i);
            if (lotRepository.findByLotCodeIgnoreCase(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "LOT-" + day.substring(2) + "-" + System.currentTimeMillis() % 10000;
    }

    private static ReceiptType parseReceiptType(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return ReceiptType.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Loại phiếu không hợp lệ: " + value);
        }
    }

    private static ReceiptStatus parseStatus(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return ReceiptStatus.fromCode(value);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Trạng thái phiếu không hợp lệ: " + value);
        }
    }
}
