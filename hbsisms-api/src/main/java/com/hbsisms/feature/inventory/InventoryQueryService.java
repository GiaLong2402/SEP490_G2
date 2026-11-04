package com.hbsisms.feature.inventory;

import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.inventory.dto.InventorySummaryResponse;
import com.hbsisms.feature.inventory.dto.LedgerRowResponse;
import com.hbsisms.feature.inventory.dto.LocationOptionResponse;
import com.hbsisms.feature.inventory.dto.LotStockRowResponse;
import com.hbsisms.feature.inventory.dto.VariantStockRowResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryQueryService {

    /** Temporary defaults until app_config reader is wired for inventory. */
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 15;

    public static final int DEFAULT_EXPIRY_WARNING_DAYS = 5;

    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final LocationRepository locationRepository;
    private final WarehouseRepository warehouseRepository;

    public InventoryQueryService(
            StockRepository stockRepository,
            StockLedgerRepository stockLedgerRepository,
            LocationRepository locationRepository,
            WarehouseRepository warehouseRepository) {
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.locationRepository = locationRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public InventorySummaryResponse summary() {
        List<VariantStockRowResponse> variants = variantRows(null, null, false);
        List<LotStockRowResponse> nearExpiry = nearExpiryRows();
        return new InventorySummaryResponse(
                variants.stream().mapToInt(VariantStockRowResponse::available).sum(),
                variants.stream().mapToInt(VariantStockRowResponse::held).sum(),
                variants.stream().mapToInt(VariantStockRowResponse::quarantine).sum(),
                nearExpiry.size(),
                (int) variants.stream().filter(VariantStockRowResponse::isLow).count(),
                DEFAULT_LOW_STOCK_THRESHOLD,
                DEFAULT_EXPIRY_WARNING_DAYS);
    }

    public PageResponse<VariantStockRowResponse> variants(
            String q, Long locationId, boolean lowOnly, int page, int pageSize) {
        return paginate(variantRows(q, locationId, lowOnly), page, pageSize);
    }

    public PageResponse<LotStockRowResponse> lots(String q, Long locationId, int page, int pageSize) {
        List<LotStockRowResponse> rows = lotRows().stream()
                .filter(r -> r.available() + r.held() + r.quarantine() + r.expired() > 0)
                .filter(r -> locationId == null || Objects.equals(r.locationId(), locationId))
                .filter(r -> matches(q, r.lotCode(), r.sku(), r.productName()))
                .sorted(byFefo())
                .toList();
        return paginate(rows, page, pageSize);
    }

    public PageResponse<LotStockRowResponse> nearExpiry(String q, int page, int pageSize) {
        List<LotStockRowResponse> rows = nearExpiryRows().stream()
                .filter(r -> matches(q, r.lotCode(), r.sku(), r.productName()))
                .toList();
        return paginate(rows, page, pageSize);
    }

    public PageResponse<LedgerRowResponse> ledger(
            String q, String entryType, LocalDate from, LocalDate to, int page, int pageSize) {
        LedgerEntryType typeFilter = parseEntryType(entryType);
        ZoneId zone = ZoneId.systemDefault();
        List<LedgerRowResponse> rows = stockLedgerRepository.findAllWithDetailsOrderByCreatedAtDesc().stream()
                .map(this::toLedgerRow)
                .filter(r -> typeFilter == null || r.entryType() == typeFilter)
                .filter(r -> matches(q, r.sku(), r.productName(), r.lotCode(), r.note()))
                .filter(r -> {
                    if (from == null && to == null) {
                        return true;
                    }
                    if (r.createdAt() == null) {
                        return false;
                    }
                    LocalDate day = r.createdAt().atZone(zone).toLocalDate();
                    if (from != null && day.isBefore(from)) {
                        return false;
                    }
                    if (to != null && day.isAfter(to)) {
                        return false;
                    }
                    return true;
                })
                .toList();
        return paginate(rows, page, pageSize);
    }

    public List<LocationOptionResponse> activeLocations() {
        boolean multiWarehouse = warehouseRepository.count() > 1;
        return locationRepository.findAllWithWarehouse().stream()
                .filter(l -> l.getWarehouse() != null && l.getWarehouse().isActive())
                .map(l -> {
                    String locationCode = formatLocationCode(l, multiWarehouse);
                    String label = l.getZone() == null || l.getZone().isBlank()
                            ? locationCode
                            : locationCode + " · " + l.getZone();
                    return new LocationOptionResponse(
                            l.getId(),
                            l.getWarehouse().getId(),
                            l.getCode(),
                            l.getZone(),
                            l.getCapacity(),
                            l.getNote(),
                            label);
                })
                .toList();
    }

    private List<LotStockRowResponse> nearExpiryRows() {
        return lotRows().stream()
                .filter(r -> r.daysLeft() != null
                        && r.daysLeft() <= DEFAULT_EXPIRY_WARNING_DAYS
                        && r.available() + r.held() + r.quarantine() + r.expired() > 0)
                .sorted(byFefo())
                .toList();
    }

    private List<VariantStockRowResponse> variantRows(String q, Long locationId, boolean lowOnly) {
        List<Stock> stocks = stockRepository.findAllWithDetails();
        Map<Long, List<Stock>> byVariant = stocks.stream()
                .collect(Collectors.groupingBy(s -> s.getVariant().getId(), LinkedHashMap::new, Collectors.toList()));

        LocalDate today = LocalDate.now();
        boolean multiWarehouse = warehouseRepository.count() > 1;
        List<VariantStockRowResponse> rows = new ArrayList<>();
        for (Map.Entry<Long, List<Stock>> entry : byVariant.entrySet()) {
            List<Stock> ownStocks = entry.getValue().stream()
                    .filter(s -> locationId == null || Objects.equals(s.getLocation().getId(), locationId))
                    .toList();
            if (ownStocks.isEmpty()) {
                continue;
            }
            List<LotStockRowResponse> own = ownStocks.stream()
                    .map(s -> toLotRow(s, today, multiWarehouse))
                    .sorted(byFefo())
                    .toList();
            List<LotStockRowResponse> visibleLots = own.stream()
                    .filter(l -> l.available() + l.held() + l.quarantine() + l.expired() > 0)
                    .toList();
            int available = own.stream().mapToInt(LotStockRowResponse::available).sum();
            int held = own.stream().mapToInt(LotStockRowResponse::held).sum();
            int quarantine = own.stream().mapToInt(LotStockRowResponse::quarantine).sum();
            int expired = own.stream().mapToInt(LotStockRowResponse::expired).sum();
            ProductVariant variant = ownStocks.get(0).getVariant();
            VariantStockRowResponse row = new VariantStockRowResponse(
                    entry.getKey(),
                    variant.getSku(),
                    variant.getPackLabel(),
                    variant.getProduct().getName(),
                    available,
                    held,
                    quarantine,
                    expired,
                    available < DEFAULT_LOW_STOCK_THRESHOLD,
                    visibleLots);
            if (lowOnly && !row.isLow()) {
                continue;
            }
            if (!matches(q, row.sku(), row.productName())) {
                continue;
            }
            rows.add(row);
        }
        rows.sort(Comparator.comparingInt(VariantStockRowResponse::available));
        return rows;
    }

    private List<LotStockRowResponse> lotRows() {
        LocalDate today = LocalDate.now();
        boolean multiWarehouse = warehouseRepository.count() > 1;
        return stockRepository.findAllWithDetails().stream()
                .map(s -> toLotRow(s, today, multiWarehouse))
                .toList();
    }

    private LotStockRowResponse toLotRow(Stock s, LocalDate today, boolean multiWarehouse) {
        ProductVariant variant = s.getVariant();
        Lot lot = s.getLot();
        Location location = s.getLocation();
        Integer daysLeft = lot.getExpDate() == null
                ? null
                : (int) ChronoUnit.DAYS.between(today, lot.getExpDate());
        return new LotStockRowResponse(
                s.getId(),
                lot.getId(),
                lot.getLotCode(),
                variant.getId(),
                variant.getSku(),
                variant.getProduct().getName(),
                location.getId(),
                formatLocationCode(location, multiWarehouse),
                lot.getMfgDate(),
                lot.getExpDate(),
                daysLeft,
                s.getQtyAvailable(),
                s.getQtyHeld(),
                s.getQtyQuarantine(),
                s.getQtyExpired());
    }

    private LedgerRowResponse toLedgerRow(StockLedger e) {
        boolean multiWarehouse = warehouseRepository.count() > 1;
        String sku = e.getVariant().getSku();
        String productName = e.getVariant().getProduct().getName();
        String lotCode = e.getLot() == null ? null : e.getLot().getLotCode();
        String locationCode = e.getLocation() == null ? null : formatLocationCode(e.getLocation(), multiWarehouse);
        String toLocationCode =
                e.getToLocation() == null ? null : formatLocationCode(e.getToLocation(), multiWarehouse);
        String creatorName = e.getCreatedBy() == null ? null : e.getCreatedBy().getFullName();
        return new LedgerRowResponse(
                e.getId(),
                e.getVariant().getId(),
                e.getLot() == null ? null : e.getLot().getId(),
                e.getLocation() == null ? null : e.getLocation().getId(),
                e.getToLocation() == null ? null : e.getToLocation().getId(),
                e.getEntryType(),
                e.getQtyDelta(),
                e.getBalanceAfter(),
                e.getRefType(),
                e.getRefId(),
                e.getNote(),
                e.getCreatedBy() == null ? null : e.getCreatedBy().getId(),
                e.getCreatedAt(),
                sku,
                productName,
                lotCode,
                locationCode,
                toLocationCode,
                creatorName);
    }

    private static String formatLocationCode(Location location, boolean multiWarehouse) {
        if (multiWarehouse) {
            return location.getWarehouse().getCode() + "/" + location.getCode();
        }
        return location.getCode();
    }

    private static Comparator<LotStockRowResponse> byFefo() {
        return Comparator.comparing(
                (LotStockRowResponse r) -> r.expDate() == null ? LocalDate.of(9999, 12, 31) : r.expDate());
    }

    private static LedgerEntryType parseEntryType(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return LedgerEntryType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean matches(String q, String... fields) {
        String needle = Strings.trimToNull(q);
        if (needle == null) {
            return true;
        }
        String lower = needle.toLowerCase(Locale.ROOT);
        for (String field : fields) {
            if (field != null && field.toLowerCase(Locale.ROOT).contains(lower)) {
                return true;
            }
        }
        return false;
    }

    private static <T> PageResponse<T> paginate(List<T> rows, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        return new PageResponse<>(rows.subList(from, to), rows.size(), safePage, safeSize);
    }
}
