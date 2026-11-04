package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.inventory.dto.FinishedReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptLineRequest;
import com.hbsisms.feature.inventory.dto.ReceiptItemResponse;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GoodsReceiptService {

    public static final String REF_TYPE = "goods_receipt";

    private static final Sort LIST_SORT = Sort.by(Sort.Direction.DESC, "receivedAt", "id");

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final RawMaterialRepository rawMaterialRepository;
    private final StockService stockService;
    private final UserRepository userRepository;

    public GoodsReceiptService(
            GoodsReceiptRepository goodsReceiptRepository,
            StockLedgerRepository stockLedgerRepository,
            ProductVariantRepository productVariantRepository,
            LocationRepository locationRepository,
            LotRepository lotRepository,
            RawMaterialRepository rawMaterialRepository,
            StockService stockService,
            UserRepository userRepository) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.productVariantRepository = productVariantRepository;
        this.locationRepository = locationRepository;
        this.lotRepository = lotRepository;
        this.rawMaterialRepository = rawMaterialRepository;
        this.stockService = stockService;
        this.userRepository = userRepository;
    }

    public PageResponse<GoodsReceiptResponse> list(String q, String receiptType, int page, int pageSize) {
        ReceiptType typeFilter = parseReceiptType(receiptType);
        Specification<GoodsReceipt> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("receiptType"), typeFilter));
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
        return PageResponse.from(goodsReceiptRepository
                .findAll(spec, PageRequests.of(page, pageSize, LIST_SORT))
                .map(this::toResponse));
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

        GoodsReceipt receipt = new GoodsReceipt();
        receipt.setCode(nextPnCode());
        receipt.setReceiptType(request.receiptType());
        receipt.setSource(source);
        receipt.setNote(Strings.trimToNull(request.note()));
        receipt.setReceivedBy(actor);
        receipt.setReceivedAt(Instant.now());
        receipt = goodsReceiptRepository.saveAndFlush(receipt);

        List<ReceiptItemResponse> items;
        if (request.receiptType() == ReceiptType.FINISHED) {
            items = receiveFinished(receipt, request.finishedLines(), actorId);
        } else if (request.receiptType() == ReceiptType.RAW) {
            items = receiveRaw(request.rawLines());
            // Persist a human summary so list can show lines until a raw-line table exists.
            String summary = items.stream()
                    .map(i -> i.name() + ": " + trimQty(i.qty()) + " " + i.unit())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
            String userNote = Strings.trimToNull(request.note());
            receipt.setNote(userNote == null ? summary : userNote + " — " + summary);
            goodsReceiptRepository.save(receipt);
        } else {
            throw ApiException.badRequest("Loại phiếu không hỗ trợ");
        }

        return GoodsReceiptResponse.from(receipt, items);
    }

    private static String trimQty(double qty) {
        if (qty == Math.rint(qty)) {
            return String.valueOf((long) qty);
        }
        return String.valueOf(qty);
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
                    lot.setReceivedAt(Instant.now());
                    return lotRepository.saveAndFlush(lot);
                });
    }

    private List<ReceiptItemResponse> receiveRaw(List<RawReceiptLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw ApiException.badRequest("Phiếu phải có ít nhất một dòng");
        }
        List<ReceiptItemResponse> items = new ArrayList<>();
        for (RawReceiptLineRequest line : lines) {
            RawMaterial material = rawMaterialRepository
                    .findById(line.rawMaterialId())
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
            if (!material.isActive()) {
                throw ApiException.conflict(material.getName() + " đã ngừng dùng");
            }
            material.setQtyOnHand(material.getQtyOnHand().add(line.qty()));
            rawMaterialRepository.save(material);
            items.add(new ReceiptItemResponse(
                    material.getName(), line.qty().doubleValue(), material.getUnit()));
        }
        return items;
    }

    private GoodsReceiptResponse toResponse(GoodsReceipt receipt) {
        List<ReceiptItemResponse> items;
        if (receipt.getReceiptType() == ReceiptType.FINISHED) {
            items = stockLedgerRepository.findByRefTypeAndRefIdWithDetails(REF_TYPE, receipt.getId()).stream()
                    .map(e -> {
                        String sku = e.getVariant().getSku();
                        String lotCode = e.getLot() == null ? "?" : e.getLot().getLotCode();
                        return new ReceiptItemResponse(sku + " · " + lotCode, e.getQtyDelta(), "khay");
                    })
                    .toList();
        } else {
            // Raw lines are not persisted separately yet (spec); list shows note if present.
            String note = Strings.trimToNull(receipt.getNote());
            items = note == null
                    ? List.of()
                    : List.of(new ReceiptItemResponse(note, 0, ""));
        }
        return GoodsReceiptResponse.from(receipt, items);
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
        String day = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.BASIC_ISO_DATE);
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
            return null;
        }
    }
}
