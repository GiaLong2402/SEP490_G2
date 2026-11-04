package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for stock mutations. Every qty change updates {@link Stock} atomically and
 * appends an immutable {@link StockLedger} row in the same transaction.
 */
@Service
public class StockService {

    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;
    private final ProductVariantRepository productVariantRepository;
    private final LotRepository lotRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    public StockService(
            StockRepository stockRepository,
            StockLedgerRepository stockLedgerRepository,
            ProductVariantRepository productVariantRepository,
            LotRepository lotRepository,
            LocationRepository locationRepository,
            UserRepository userRepository) {
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
        this.productVariantRepository = productVariantRepository;
        this.lotRepository = lotRepository;
        this.locationRepository = locationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Stock receiveIn(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        assertCapacityAllows(cmd.locationId(), cmd.qty());
        Stock stock = getOrCreateStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.IN,
                cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock issueOut(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        Stock stock = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), -cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.OUT,
                -cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock hold(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        Stock stock = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), -cmd.qty());
        applyHeldDelta(stock.getId(), cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.HOLD,
                -cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock release(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        Stock stock = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        applyHeldDelta(stock.getId(), -cmd.qty());
        int balance = applyAvailableDelta(stock.getId(), cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.RELEASE,
                cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock move(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        if (cmd.toLocationId() == null) {
            throw ApiException.badRequest("Vị trí đích là bắt buộc");
        }
        if (cmd.locationId() == cmd.toLocationId()) {
            throw ApiException.badRequest("Vị trí đích trùng vị trí hiện tại");
        }
        Stock source = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        Location toLocation = locationRepository
                .findById(cmd.toLocationId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí đích"));
        assertLocationCapacityAllows(toLocation, cmd.qty());
        Long fromWarehouseId = source.getLocation().getWarehouse().getId();
        Long toWarehouseId = toLocation.getWarehouse().getId();
        // Same-warehouse move does not increase warehouse occupancy.
        if (!fromWarehouseId.equals(toWarehouseId)) {
            assertWarehouseCapacityAllows(toLocation.getWarehouse(), cmd.qty());
        }
        applyAvailableDelta(source.getId(), -cmd.qty());
        Stock target = getOrCreateStock(cmd.variantId(), cmd.lotId(), cmd.toLocationId());
        int balance = applyAvailableDelta(target.getId(), cmd.qty());
        appendLedger(
                source,
                toLocation,
                LedgerEntryType.MOVE,
                cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note() != null ? cmd.note() : "Chuyển vị trí",
                cmd.actorId());
        return requireStock(target.getId());
    }

    /** {@code delta} may be positive or negative; zero is rejected. */
    @Transactional
    public Stock adjust(StockMutation cmd, int signedDelta) {
        if (signedDelta == 0) {
            throw ApiException.badRequest("Số lượng điều chỉnh phải khác 0");
        }
        if (Strings.trimToNull(cmd.note()) == null) {
            throw ApiException.badRequest("Vui lòng nhập lý do điều chỉnh");
        }
        if (signedDelta > 0) {
            assertCapacityAllows(cmd.locationId(), signedDelta);
        }
        Stock stock = signedDelta > 0
                ? getOrCreateStock(cmd.variantId(), cmd.lotId(), cmd.locationId())
                : requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), signedDelta);
        appendLedger(
                stock,
                null,
                LedgerEntryType.ADJUST,
                signedDelta,
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock damage(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        Stock stock = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), -cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.DAMAGE,
                -cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    @Transactional
    public Stock expire(StockMutation cmd) {
        requirePositiveQty(cmd.qty());
        Stock stock = requireExistingStock(cmd.variantId(), cmd.lotId(), cmd.locationId());
        int balance = applyAvailableDelta(stock.getId(), -cmd.qty());
        applyExpiredDelta(stock.getId(), cmd.qty());
        appendLedger(
                stock,
                null,
                LedgerEntryType.EXPIRE,
                -cmd.qty(),
                balance,
                cmd.refType(),
                cmd.refId(),
                cmd.note(),
                cmd.actorId());
        return requireStock(stock.getId());
    }

    /** Move by stock row id — used by {@code POST /inventory/moves}. */
    @Transactional
    public Stock moveByStockId(long stockId, long toLocationId, int qty, Long actorId) {
        Stock source = stockRepository
                .findWithDetailsById(stockId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn"));
        return move(StockMutation.move(
                source.getVariant().getId(),
                source.getLot().getId(),
                source.getLocation().getId(),
                toLocationId,
                qty,
                "Chuyển vị trí",
                actorId));
    }

    /** Adjust by stock row id — used by {@code POST /inventory/adjusts}. */
    @Transactional
    public Stock adjustByStockId(long stockId, int qtyDelta, String note, Long actorId) {
        Stock source = stockRepository
                .findWithDetailsById(stockId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn"));
        return adjust(
                StockMutation.of(
                        source.getVariant().getId(),
                        source.getLot().getId(),
                        source.getLocation().getId(),
                        Math.abs(qtyDelta),
                        null,
                        null,
                        note,
                        actorId),
                qtyDelta);
    }

    private Stock getOrCreateStock(long variantId, long lotId, long locationId) {
        return stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variantId, lotId, locationId)
                .orElseGet(() -> {
                    ProductVariant variant = productVariantRepository
                            .findById(variantId)
                            .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách sản phẩm"));
                    Lot lot = lotRepository
                            .findById(lotId)
                            .orElseThrow(() -> ApiException.notFound("Không tìm thấy lô"));
                    if (!lot.getVariant().getId().equals(variantId)) {
                        throw ApiException.badRequest("Lô không thuộc quy cách đã chọn");
                    }
                    Location location = locationRepository
                            .findById(locationId)
                            .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
                    Stock created = new Stock();
                    created.setVariant(variant);
                    created.setLot(lot);
                    created.setLocation(location);
                    created.setQtyAvailable(0);
                    created.setQtyHeld(0);
                    created.setQtyQuarantine(0);
                    created.setQtyExpired(0);
                    return stockRepository.saveAndFlush(created);
                });
    }

    private Stock requireExistingStock(long variantId, long lotId, long locationId) {
        return stockRepository
                .findByVariant_IdAndLot_IdAndLocation_Id(variantId, lotId, locationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn"));
    }

    private Stock requireStock(long id) {
        return stockRepository.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn"));
    }

    private int applyAvailableDelta(long stockId, int delta) {
        int updated = stockRepository.adjustQtyAvailable(stockId, delta);
        if (updated == 0) {
            throw ApiException.conflict("Không đủ tồn kho");
        }
        return stockRepository
                .findById(stockId)
                .map(Stock::getQtyAvailable)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng tồn"));
    }

    private void applyHeldDelta(long stockId, int delta) {
        int updated = stockRepository.adjustQtyHeld(stockId, delta);
        if (updated == 0) {
            throw ApiException.conflict("Không đủ tồn đang giữ");
        }
    }

    private void applyExpiredDelta(long stockId, int delta) {
        int updated = stockRepository.adjustQtyExpired(stockId, delta);
        if (updated == 0) {
            throw ApiException.conflict("Không thể cập nhật tồn hết hạn");
        }
    }

    private void appendLedger(
            Stock stock,
            Location toLocation,
            LedgerEntryType entryType,
            int qtyDelta,
            int balanceAfter,
            String refType,
            Long refId,
            String note,
            Long actorId) {
        StockLedger ledger = new StockLedger();
        ledger.setVariant(stock.getVariant());
        ledger.setLot(stock.getLot());
        ledger.setLocation(stock.getLocation());
        ledger.setToLocation(toLocation);
        ledger.setEntryType(entryType);
        ledger.setQtyDelta(qtyDelta);
        ledger.setBalanceAfter(balanceAfter);
        ledger.setRefType(Strings.trimToNull(refType));
        ledger.setRefId(refId);
        ledger.setNote(Strings.trimToNull(note));
        if (actorId != null) {
            AppUser actor = userRepository.getReferenceById(actorId);
            ledger.setCreatedBy(actor);
        }
        stockLedgerRepository.save(ledger);
    }

    private static void requirePositiveQty(int qty) {
        if (qty <= 0) {
            throw ApiException.badRequest("Số lượng phải là số nguyên dương");
        }
    }

    /** Reject inbound qty that would exceed location or warehouse capacity (both required). */
    private void assertCapacityAllows(long locationId, int qtyToAdd) {
        Location location = locationRepository
                .findById(locationId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vị trí"));
        assertLocationCapacityAllows(location, qtyToAdd);
        assertWarehouseCapacityAllows(location.getWarehouse(), qtyToAdd);
    }

    private void assertLocationCapacityAllows(Location location, int qtyToAdd) {
        int capacity = requireCapacity(location.getCapacity(), "vị trí " + location.getCode());
        long used = stockRepository.sumQtyByLocationId(location.getId());
        long after = used + qtyToAdd;
        if (after > capacity) {
            throw ApiException.conflict(String.format(
                    "Vị trí %s sức chứa %d khay, đang %d, không nhận thêm %d (vượt %d).",
                    location.getCode(),
                    capacity,
                    used,
                    qtyToAdd,
                    after - capacity));
        }
    }

    private void assertWarehouseCapacityAllows(Warehouse warehouse, int qtyToAdd) {
        int capacity = requireCapacity(warehouse.getCapacity(), "kho " + warehouse.getCode());
        long used = stockRepository.sumQtyByWarehouseId(warehouse.getId());
        long after = used + qtyToAdd;
        if (after > capacity) {
            throw ApiException.conflict(String.format(
                    "Kho %s sức chứa %d khay, đang %d, không nhận thêm %d (vượt %d).",
                    warehouse.getCode(),
                    capacity,
                    used,
                    qtyToAdd,
                    after - capacity));
        }
    }

    private static int requireCapacity(Integer capacity, String label) {
        if (capacity == null || capacity <= 0) {
            throw ApiException.badRequest("Sức chứa " + label + " chưa được cấu hình");
        }
        return capacity;
    }
}
