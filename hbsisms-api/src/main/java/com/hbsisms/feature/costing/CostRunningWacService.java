package com.hbsisms.feature.costing;

import com.hbsisms.feature.inventory.RawMaterialLot;
import com.hbsisms.feature.inventory.RawMaterialLotRepository;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * WAC liên hoàn (NVL + TP) lưu trên {@link CostRunningBalance} — không phụ thuộc kỳ OPEN.
 */
@Service
public class CostRunningWacService {

    private static final Logger log = LoggerFactory.getLogger(CostRunningWacService.class);
    private static final int MONEY_SCALE = 2;

    private final CostRunningBalanceRepository runningRepository;
    private final CostPeriodRepository periodRepository;
    private final CostItemBalanceRepository balanceRepository;
    private final RawMaterialLotRepository rawMaterialLotRepository;
    private final StockRepository stockRepository;

    public CostRunningWacService(
            CostRunningBalanceRepository runningRepository,
            CostPeriodRepository periodRepository,
            CostItemBalanceRepository balanceRepository,
            RawMaterialLotRepository rawMaterialLotRepository,
            StockRepository stockRepository) {
        this.runningRepository = runningRepository;
        this.periodRepository = periodRepository;
        this.balanceRepository = balanceRepository;
        this.rawMaterialLotRepository = rawMaterialLotRepository;
        this.stockRepository = stockRepository;
    }

    public Optional<BigDecimal> currentAvgUnitCost(CostItemType itemType, Long itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        return runningRepository
                .findByItemTypeAndItemId(itemType, itemId)
                .map(CostRunningBalance::getAvgUnitCost)
                .filter(c -> c != null && c.signum() > 0);
    }

    public List<CostRunningBalance> listWithStock() {
        return runningRepository.findAllByOrderByItemTypeAscItemIdAsc().stream()
                .filter(r -> r.getQtyOnHand().signum() > 0 || r.getInventoryValue().signum() > 0)
                .toList();
    }

    @Transactional
    public void applyInbound(CostItemType itemType, Long itemId, BigDecimal qty, BigDecimal unitPrice) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return;
        }
        CostRunningBalance row = getOrCreateForUpdate(itemType, itemId);
        row.applyInbound(qty, unitPrice);
        runningRepository.save(row);
    }

    /**
     * Trừ tồn theo ĐG WAC hiện hành. Trả về đơn giá dùng (để mirror sổ kỳ).
     */
    @Transactional
    public BigDecimal applyOutbound(CostItemType itemType, Long itemId, BigDecimal qty) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        CostRunningBalance row = getOrCreateForUpdate(itemType, itemId);
        BigDecimal unit = row.applyOutbound(qty);
        runningRepository.save(row);
        return unit;
    }

    /** Chỉ tạo nếu chưa có — dùng bootstrap / seed kỳ / migrate lúc đọc. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensurePresent(CostItemType itemType, Long itemId, BigDecimal qty, BigDecimal value) {
        if (itemId == null) {
            return;
        }
        if (runningRepository.findByItemTypeAndItemId(itemType, itemId).isPresent()) {
            return;
        }
        CostRunningBalance row = CostRunningBalance.of(itemType, itemId, qty, value);
        runningRepository.save(row);
    }

    /**
     * Bootstrap lần đầu: OPEN → CLOSED mới nhất → snapshot lô vật lý.
     * Chỉ insert item chưa có dòng running.
     */
    @Transactional
    public int bootstrapMissing() {
        int created = 0;
        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isPresent()) {
            created += seedFromPeriodBalances(open.get().getId());
        }
        if (created == 0) {
            Optional<CostPeriod> closed =
                    periodRepository.findFirstByStatusOrderByPeriodStartDesc(CostPeriodStatus.CLOSED);
            if (closed.isPresent()) {
                created += seedFromPeriodBalances(closed.get().getId());
            }
        }
        created += seedFromPhysicalSnapshot();
        if (created > 0) {
            log.info("Bootstrapped {} cost_running_balance row(s)", created);
        }
        return created;
    }

    private int seedFromPeriodBalances(Long periodId) {
        int created = 0;
        for (CostItemBalance bal :
                balanceRepository.findByPeriod_IdOrderByItemTypeAscItemIdAsc(periodId)) {
            BigDecimal qty = bal.getClosingQty() != null ? bal.getClosingQty() : BigDecimal.ZERO;
            BigDecimal value = bal.getClosingValue() != null ? bal.getClosingValue() : BigDecimal.ZERO;
            if (qty.signum() == 0 && value.signum() == 0) {
                continue;
            }
            if (runningRepository.findByItemTypeAndItemId(bal.getItemType(), bal.getItemId()).isEmpty()) {
                CostRunningBalance row =
                        CostRunningBalance.of(bal.getItemType(), bal.getItemId(), qty, value);
                if (bal.getAvgUnitCost() != null && row.getAvgUnitCost() == null) {
                    row.setAvgUnitCost(bal.getAvgUnitCost());
                }
                runningRepository.save(row);
                created++;
            }
        }
        return created;
    }

    private int seedFromPhysicalSnapshot() {
        int created = 0;
        Map<Long, QtyValue> raw = new HashMap<>();
        for (RawMaterialLot lot : rawMaterialLotRepository.findAllWithRemaining()) {
            if (lot.getQtyRemaining() == null || lot.getQtyRemaining().signum() <= 0) {
                continue;
            }
            BigDecimal price = lot.getUnitPrice() == null ? BigDecimal.ZERO : lot.getUnitPrice();
            BigDecimal value = lot.getQtyRemaining().multiply(price).setScale(2, RoundingMode.HALF_UP);
            raw.merge(
                    lot.getRawMaterial().getId(),
                    new QtyValue(lot.getQtyRemaining(), value),
                    QtyValue::add);
        }
        for (Map.Entry<Long, QtyValue> e : raw.entrySet()) {
            if (runningRepository
                    .findByItemTypeAndItemId(CostItemType.RAW_MATERIAL, e.getKey())
                    .isEmpty()) {
                runningRepository.save(CostRunningBalance.of(
                        CostItemType.RAW_MATERIAL, e.getKey(), e.getValue().qty(), e.getValue().value()));
                created++;
            }
        }

        Map<Long, QtyValue> finished = new HashMap<>();
        for (Stock stock : stockRepository.findAllWithDetails()) {
            int qty = stock.getQtyAvailable() + stock.getQtyHeld();
            if (qty <= 0) {
                continue;
            }
            BigDecimal unitCost =
                    stock.getLot().getUnitCost() == null ? BigDecimal.ZERO : stock.getLot().getUnitCost();
            BigDecimal q = BigDecimal.valueOf(qty);
            BigDecimal value = q.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
            finished.merge(stock.getVariant().getId(), new QtyValue(q, value), QtyValue::add);
        }
        for (Map.Entry<Long, QtyValue> e : finished.entrySet()) {
            if (runningRepository
                    .findByItemTypeAndItemId(CostItemType.FINISHED, e.getKey())
                    .isEmpty()) {
                runningRepository.save(CostRunningBalance.of(
                        CostItemType.FINISHED, e.getKey(), e.getValue().qty(), e.getValue().value()));
                created++;
            }
        }
        return created;
    }

    private CostRunningBalance getOrCreateForUpdate(CostItemType type, Long itemId) {
        Optional<CostRunningBalance> existing = runningRepository.findForUpdate(type, itemId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CostRunningBalance created = CostRunningBalance.empty(type, itemId);
        runningRepository.saveAndFlush(created);
        return runningRepository.findForUpdate(type, itemId).orElse(created);
    }

    private record QtyValue(BigDecimal qty, BigDecimal value) {
        QtyValue add(QtyValue other) {
            return new QtyValue(qty.add(other.qty), value.add(other.value));
        }
    }
}
