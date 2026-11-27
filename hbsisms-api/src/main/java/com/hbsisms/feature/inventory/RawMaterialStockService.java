package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.costing.CostItemType;
import com.hbsisms.feature.costing.CostingMovementService;
import com.hbsisms.feature.costing.CostingService;
import com.hbsisms.feature.user.AppUser;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Một chỗ duy nhất thay đổi tồn nguyên vật liệu theo lô (Inter 1 dòng 47): tạo lô, cộng {@code qtyOnHand}
 * và ghi sổ kho NVL trong cùng transaction. Luôn giữ tổng {@code qtyRemaining} các lô = {@code qtyOnHand}.
 * Dòng 50 (xuất NVL cho xưởng): FEFO chọn lô; định giá IssueSlice / sổ xuất theo WAC NVL
 * (fallback giá lô nếu chưa có ĐG kỳ).
 */
@Service
public class RawMaterialStockService {

    private static final Logger log = LoggerFactory.getLogger(RawMaterialStockService.class);

    /** refType trên sổ kho NVL cho lô tồn đầu kỳ (tồn có từ trước khi quản lý theo lô). */
    public static final String REF_OPENING = "opening_balance";

    /** refType xuất NVL cho lệnh sản xuất (phiếu xuất). */
    public static final String REF_MATERIAL_ISSUE = "material_issue";

    /** refType trả NVL dư về lô gốc sau sản xuất (refId = id lệnh). */
    public static final String REF_PRODUCTION_ORDER = "production_order";

    /** refType bỏ lô (refId = id lô). */
    public static final String REF_LOT_DISCARD = "raw_lot_discard";

    static final String COLD_ZONE_PREFIX = "LANH";

    static final String OPENING_LOT_CODE = "TON-DAU";

    /** Một lát xuất từ đúng một lô (task 50). */
    public record IssueSlice(RawMaterialLot lot, BigDecimal qty, BigDecimal unitCost) {}

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter LOT_DAY = DateTimeFormatter.ofPattern("yyMMdd");

    private final RawMaterialRepository rawMaterialRepository;
    private final RawMaterialLotRepository rawMaterialLotRepository;
    private final RawMaterialLedgerRepository rawMaterialLedgerRepository;
    private final CostingMovementService costingMovementService;
    private final CostingService costingService;
    private final Clock clock;

    public RawMaterialStockService(
            RawMaterialRepository rawMaterialRepository,
            RawMaterialLotRepository rawMaterialLotRepository,
            RawMaterialLedgerRepository rawMaterialLedgerRepository,
            CostingMovementService costingMovementService,
            CostingService costingService,
            Clock clock) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
        this.rawMaterialLedgerRepository = rawMaterialLedgerRepository;
        this.costingMovementService = costingMovementService;
        this.costingService = costingService;
        this.clock = clock;
    }

    /**
     * Nhập một lô NVL. {@code material} phải đã được khóa ({@link RawMaterialRepository#findByIdForUpdate}).
     *
     * @param lotCode trống → tự sinh {@code NVL-yyMMdd-NN} (duy nhất trong NVL đó)
     * @return lô vừa tạo
     */
    @Transactional
    public RawMaterialLot receive(
            RawMaterial material,
            String lotCode,
            LocalDate mfgDate,
            LocalDate expDate,
            BigDecimal unitPrice,
            BigDecimal qty,
            GoodsReceipt receipt,
            AppUser actor,
            String note) {
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Số lượng nhập phải lớn hơn 0");
        }
        String code = Strings.trimToNull(lotCode);
        if (code == null) {
            code = autoLotCode(material);
        } else if (rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(material.getId(), code)) {
            throw ApiException.conflict("Mã lô " + code + " đã có cho nguyên liệu " + material.getName());
        }

        Instant now = Instant.now(clock);
        RawMaterialLot lot = new RawMaterialLot();
        lot.setRawMaterial(material);
        lot.setLotCode(code);
        lot.setMfgDate(mfgDate);
        lot.setExpDate(expDate);
        lot.setUnitPrice(unitPrice);
        lot.setQtyReceived(qty);
        lot.setQtyRemaining(qty);
        lot.setReceipt(receipt);
        lot.setReceivedAt(now);
        lot = rawMaterialLotRepository.saveAndFlush(lot);

        BigDecimal balance = material.getQtyOnHand().add(qty);
        material.setQtyOnHand(balance);
        rawMaterialRepository.save(material);

        RawMaterialLedger entry = new RawMaterialLedger();
        entry.setRawMaterial(material);
        entry.setLot(lot);
        entry.setEntryType(RawLedgerEntryType.IN);
        entry.setQtyDelta(qty);
        entry.setBalanceAfter(balance);
        entry.setUnitPrice(unitPrice);
        entry.setRefType(receipt == null ? null : GoodsReceiptService.REF_TYPE);
        entry.setRefId(receipt == null ? null : receipt.getId());
        entry.setNote(note);
        entry.setCreatedBy(actor);
        entry.setCreatedAt(now);
        rawMaterialLedgerRepository.save(entry);
        costingMovementService.recordInbound(
                CostItemType.RAW_MATERIAL,
                material.getId(),
                qty,
                unitPrice,
                receipt == null ? null : GoodsReceiptService.REF_TYPE,
                receipt == null ? null : receipt.getId());
        return lot;
    }

    /**
     * Khu lạnh của kho nguyên liệu = khu có mã bắt đầu bằng {@code LANH} (LANH, LANH-2…). Đồ khu lạnh dư sau sản xuất
     * tách thành lô -TL hạn ngắn; đồ khu khác trả thẳng về lô đã xuất.
     */
    public static boolean isColdStorage(RawMaterial material) {
        Location zone = material == null ? null : material.getStorageLocation();
        String code = zone == null || zone.getCode() == null ? "" : zone.getCode().trim().toUpperCase(Locale.ROOT);
        return code.startsWith(COLD_ZONE_PREFIX);
    }

    /**
     * Cộng lại phần dư vào chính lô đã xuất (đồ khu khô): giữ mã lô, HSD, đơn giá; ghi sổ IN gắn {@code refType/refId}.
     */
    @Transactional
    public RawMaterialLot returnToLot(Long lotId, BigDecimal qty, String refType, Long refId, AppUser actor, String note) {
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Số lượng trả lại phải lớn hơn 0");
        }
        RawMaterialLot lot = rawMaterialLotRepository
                .findByIdForUpdate(lotId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lô nguyên liệu"));
        RawMaterial material = rawMaterialRepository
                .findByIdForUpdate(lot.getRawMaterial().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));
        Instant now = Instant.now(clock);
        lot.setQtyRemaining(lot.getQtyRemaining().add(qty));
        rawMaterialLotRepository.save(lot);

        BigDecimal balance = material.getQtyOnHand().add(qty);
        material.setQtyOnHand(balance);
        rawMaterialRepository.save(material);

        writeLedger(material, lot, RawLedgerEntryType.IN, qty, balance, refType, refId, actor, note, now);
        costingMovementService.recordInbound(
                CostItemType.RAW_MATERIAL, material.getId(), qty, lot.getUnitPrice(), refType, refId);
        return lot;
    }

    /**
     * Dòng 54 — bỏ phần còn lại của lô (hết hạn / hư hỏng / khác): số còn về 0, trừ tồn NVL, ghi sổ DISCARD. Không cần duyệt.
     */
    @Transactional
    public RawMaterialLot discard(Long lotId, String reasonCode, String note, AppUser actor) {
        RawLotDiscardReason reason;
        try {
            reason = RawLotDiscardReason.fromCode(reasonCode);
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest(ex.getMessage());
        }
        String cleanNote = Strings.trimToNull(note);
        if (reason == RawLotDiscardReason.OTHER && cleanNote == null) {
            throw ApiException.badRequest("Ghi rõ lý do khi chọn \"Khác\"");
        }
        RawMaterialLot lot = rawMaterialLotRepository
                .findByIdForUpdate(lotId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lô nguyên liệu"));
        BigDecimal qty = lot.getQtyRemaining();
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.conflict("Lô " + lot.getLotCode() + " đã hết, không còn gì để bỏ");
        }
        LocalDate today = LocalDate.now(clock.withZone(VN));
        if (reason == RawLotDiscardReason.EXPIRED && (lot.getExpDate() == null || !lot.getExpDate().isBefore(today))) {
            throw ApiException.badRequest("Lô " + lot.getLotCode() + " chưa hết hạn, chọn Hư hỏng hoặc Khác");
        }
        RawMaterial material = rawMaterialRepository
                .findByIdForUpdate(lot.getRawMaterial().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nguyên liệu"));

        Instant now = Instant.now(clock);
        lot.setQtyRemaining(BigDecimal.ZERO);
        rawMaterialLotRepository.save(lot);

        BigDecimal balance = material.getQtyOnHand().subtract(qty).max(BigDecimal.ZERO);
        material.setQtyOnHand(balance);
        rawMaterialRepository.save(material);

        String ledgerNote = "Bỏ lô " + lot.getLotCode() + " · " + reason.label() + (cleanNote == null ? "" : " · " + cleanNote);
        writeLedger(material, lot, RawLedgerEntryType.DISCARD, qty.negate(), balance, REF_LOT_DISCARD, lot.getId(), actor, ledgerNote, now);
        costingMovementService.recordOutbound(
                CostItemType.RAW_MATERIAL, material.getId(), qty, REF_LOT_DISCARD, lot.getId());
        log.info("Discarded raw lot {} qty={} reason={}", lot.getLotCode(), qty, reason);
        return lot;
    }

    private void writeLedger(
            RawMaterial material,
            RawMaterialLot lot,
            RawLedgerEntryType type,
            BigDecimal qtyDelta,
            BigDecimal balance,
            String refType,
            Long refId,
            AppUser actor,
            String note,
            Instant at) {
        RawMaterialLedger entry = new RawMaterialLedger();
        entry.setRawMaterial(material);
        entry.setLot(lot);
        entry.setEntryType(type);
        entry.setQtyDelta(qtyDelta);
        entry.setBalanceAfter(balance);
        entry.setUnitPrice(lot.getUnitPrice() == null ? BigDecimal.ZERO : lot.getUnitPrice());
        entry.setRefType(refType);
        entry.setRefId(refId);
        entry.setNote(note);
        entry.setCreatedBy(actor);
        entry.setCreatedAt(at);
        rawMaterialLedgerRepository.save(entry);
    }

    /** Lô của NVL theo thứ tự FEFO; {@code availableOnly} = chỉ lô còn tồn. */
    @Transactional(readOnly = true)
    public List<RawMaterialLot> lots(Long rawMaterialId, boolean availableOnly) {
        if (!rawMaterialRepository.existsById(rawMaterialId)) {
            throw ApiException.notFound("Không tìm thấy nguyên liệu");
        }
        return rawMaterialLotRepository.findFefo(rawMaterialId, availableOnly);
    }

    /**
     * Xuất đúng một lô. {@code material} phải đã khóa {@code findByIdForUpdate}.
     * Lô được khóa lại trong method.
     */
    @Transactional
    public IssueSlice issueFromLot(
            RawMaterial material,
            Long lotId,
            BigDecimal qty,
            String refType,
            Long refId,
            AppUser actor,
            String note) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(material.getId(), "material id");
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Số lượng xuất phải lớn hơn 0");
        }
        RawMaterialLot lot = rawMaterialLotRepository
                .findByIdForUpdate(lotId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy lô nguyên liệu"));
        if (!lot.getRawMaterial().getId().equals(material.getId())) {
            throw ApiException.badRequest("Lô không thuộc nguyên liệu " + material.getName());
        }
        if (lot.getQtyRemaining().compareTo(qty) < 0) {
            throw ApiException.conflict(
                    "Lô " + lot.getLotCode() + " không đủ tồn (còn " + lot.getQtyRemaining() + ")");
        }
        Instant now = Instant.now(clock);
        lot.setQtyRemaining(lot.getQtyRemaining().subtract(qty));
        rawMaterialLotRepository.save(lot);

        BigDecimal balance = material.getQtyOnHand().subtract(qty);
        if (balance.signum() < 0) {
            throw ApiException.conflict("Không đủ tồn nguyên liệu " + material.getName());
        }
        material.setQtyOnHand(balance);
        rawMaterialRepository.save(material);

        // FEFO đã chọn lô; tiền xuất SX / IssueSlice = WAC NVL (fallback giá lô).
        BigDecimal lotPrice = lot.getUnitPrice() == null ? BigDecimal.ZERO : lot.getUnitPrice();
        BigDecimal unitCost = costingService.resolveRawIssueUnitCost(material.getId(), lotPrice);
        RawMaterialLedger entry = new RawMaterialLedger();
        entry.setRawMaterial(material);
        entry.setLot(lot);
        entry.setEntryType(RawLedgerEntryType.OUT);
        entry.setQtyDelta(qty.negate());
        entry.setBalanceAfter(balance);
        entry.setUnitPrice(unitCost);
        entry.setRefType(refType);
        entry.setRefId(refId);
        entry.setNote(note);
        entry.setCreatedBy(actor);
        entry.setCreatedAt(now);
        rawMaterialLedgerRepository.save(entry);
        // Task 56 — xuất ghi giá tạm WAC trên sổ kỳ.
        costingMovementService.recordOutbound(
                CostItemType.RAW_MATERIAL, material.getId(), qty, refType, refId);
        return new IssueSlice(lot, qty, unitCost);
    }

    /**
     * Xuất theo FEFO đủ {@code qty}, bỏ qua lô đã hết hạn. {@code material} phải đã khóa.
     *
     * @return các lát (một phần tử / lô)
     */
    @Transactional
    public List<IssueSlice> issueFefo(
            RawMaterial material,
            BigDecimal qty,
            String refType,
            Long refId,
            AppUser actor,
            String note) {
        Objects.requireNonNull(material, "material");
        if (qty == null || qty.signum() <= 0) {
            throw ApiException.badRequest("Số lượng xuất phải lớn hơn 0");
        }
        LocalDate today = LocalDate.now(clock.withZone(VN));
        BigDecimal usable = rawMaterialLotRepository.sumUsableRemaining(material.getId(), today);
        if (usable == null) {
            usable = BigDecimal.ZERO;
        }
        if (usable.compareTo(qty) < 0) {
            throw ApiException.conflict(
                    "Không đủ tồn nguyên liệu dùng được " + material.getName()
                            + " (còn " + usable + ", chưa tính lô hết hạn)");
        }
        BigDecimal remaining = qty;
        List<IssueSlice> slices = new ArrayList<>();
        for (RawMaterialLot listed : rawMaterialLotRepository.findFefoUsable(material.getId(), today)) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal take = listed.getQtyRemaining().min(remaining);
            if (take.signum() <= 0) {
                continue;
            }
            slices.add(issueFromLot(material, listed.getId(), take, refType, refId, actor, note));
            remaining = remaining.subtract(take);
        }
        if (remaining.signum() > 0) {
            throw ApiException.conflict(
                    "Không đủ tồn theo lô cho " + material.getName()
                            + " — còn thiếu " + remaining.setScale(3, RoundingMode.HALF_UP));
        }
        return slices;
    }

    /**
     * Tồn có từ trước khi quản lý theo lô (cộng thẳng {@code qtyOnHand}) → tạo 1 lô "tồn đầu kỳ" cho phần
     * chênh lệch để tổng các lô khớp tổng tồn. Không đổi {@code qtyOnHand}. Chạy lại không tạo trùng.
     *
     * @return số NVL đã tạo lô tồn đầu kỳ
     */
    @Transactional
    public int ensureOpeningLots() {
        int created = 0;
        for (RawMaterial listed : rawMaterialRepository.findAllByOrderByCodeAsc()) {
            RawMaterial material = rawMaterialRepository.findByIdForUpdate(listed.getId()).orElse(null);
            if (material == null) {
                continue;
            }
            BigDecimal inLots = rawMaterialLotRepository.sumRemaining(material.getId());
            BigDecimal gap = material.getQtyOnHand().subtract(inLots == null ? BigDecimal.ZERO : inLots);
            if (gap.signum() == 0) {
                continue;
            }
            if (gap.signum() < 0) {
                log.warn("Raw material {} has lots ({}) above qtyOnHand ({}) — left unchanged",
                        material.getCode(), inLots, material.getQtyOnHand());
                continue;
            }
            String code = OPENING_LOT_CODE;
            for (int i = 2; rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(material.getId(), code); i++) {
                code = OPENING_LOT_CODE + "-" + i;
            }
            Instant now = Instant.now(clock);
            RawMaterialLot lot = new RawMaterialLot();
            lot.setRawMaterial(material);
            lot.setLotCode(code);
            lot.setQtyReceived(gap);
            lot.setQtyRemaining(gap);
            lot.setReceivedAt(now);
            lot.setNote("Tồn đầu kỳ (có trước khi quản lý theo lô)");
            lot = rawMaterialLotRepository.saveAndFlush(lot);

            RawMaterialLedger entry = new RawMaterialLedger();
            entry.setRawMaterial(material);
            entry.setLot(lot);
            entry.setEntryType(RawLedgerEntryType.ADJUST);
            entry.setQtyDelta(gap);
            entry.setBalanceAfter(material.getQtyOnHand());
            entry.setRefType(REF_OPENING);
            entry.setNote("Tồn đầu kỳ");
            entry.setCreatedAt(now);
            rawMaterialLedgerRepository.save(entry);
            created++;
        }
        return created;
    }

    /** Đuôi lô trả lại ở cuối mã: -TL, -TL2, -TL3… (có thể lặp nếu dữ liệu cũ là -TL-TL). */
    private static final Pattern LEFTOVER_SUFFIX = Pattern.compile("(?i)(-TL\\d*)+$");

    /**
     * Mã lô cho nguyên liệu dư trả lại kho: lấy mã lô gốc (bỏ đuôi -TL… nếu lô xuất ra vốn là lô trả lại),
     * rồi chọn mã đầu tiên còn trống của nguyên liệu đó: {@code gốc-TL}, {@code gốc-TL2}, {@code gốc-TL3}…
     */
    public String nextLeftoverLotCode(RawMaterial material, String sourceLotCode) {
        String source = Strings.trimToNull(sourceLotCode);
        String base = source == null ? "" : LEFTOVER_SUFFIX.matcher(source).replaceFirst("");
        if (base.isEmpty()) {
            return autoLotCode(material) + "-TL";
        }
        for (int i = 1; i <= 999; i++) {
            String candidate = base + (i == 1 ? "-TL" : "-TL" + i);
            if (!rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(material.getId(), candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Không sinh được mã lô trả lại cho " + material.getName());
    }

    private String autoLotCode(RawMaterial material) {
        String day = LocalDate.now(clock.withZone(VN)).format(LOT_DAY);
        for (int i = 1; i <= 999; i++) {
            String candidate = "NVL-" + day + "-" + String.format(Locale.ROOT, "%02d", i);
            if (!rawMaterialLotRepository.existsByRawMaterial_IdAndLotCodeIgnoreCase(material.getId(), candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Không sinh được mã lô - vui lòng nhập mã lô");
    }
}
