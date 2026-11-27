package com.hbsisms.feature.costing;

import com.hbsisms.common.exception.ApiException;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhận nhập/xuất vào WAC liên hoàn ({@link CostRunningWacService}) luôn;
 * nếu có kỳ OPEN thì mirror sang {@link CostItemBalance} (báo cáo sổ kỳ).
 * Nhập: giá trị thật → ĐG = (GT trước + GT nhập) / (SL trước + SL nhập).
 * Xuất: giá tạm = ĐG WAC liên hoàn hiện hành trên số dư chạy.
 * Chuyển vị trí không gọi service này.
 */
@Service
public class CostingMovementService {

    private static final Logger log = LoggerFactory.getLogger(CostingMovementService.class);

    private final CostPeriodRepository periodRepository;
    private final CostItemBalanceRepository balanceRepository;
    private final CostRunningWacService runningWacService;

    public CostingMovementService(
            CostPeriodRepository periodRepository,
            CostItemBalanceRepository balanceRepository,
            CostRunningWacService runningWacService) {
        this.periodRepository = periodRepository;
        this.balanceRepository = balanceRepository;
        this.runningWacService = runningWacService;
    }

    /**
     * Cộng nhập vào WAC chạy; mirror kỳ OPEN nếu có.
     */
    @Transactional
    public void recordInbound(
            CostItemType itemType, Long itemId, BigDecimal qty, BigDecimal unitPrice, String refType, Long refId) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return;
        }
        runningWacService.applyInbound(itemType, itemId, qty, unitPrice);

        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isEmpty()) {
            log.debug("WAC running ok, no OPEN period to mirror {} #{} ({}/{})", itemType, itemId, refType, refId);
            return;
        }
        CostPeriod period = open.get();
        assertMutable(period);
        CostItemBalance row = getOrCreateForUpdate(period, itemType, itemId);
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal value = qty.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP);
        row.applyInbound(qty, value);
        balanceRepository.save(row);
    }

    /**
     * Cộng xuất theo ĐG WAC chạy; mirror kỳ OPEN nếu có. Không dùng giá lô vật lý.
     */
    @Transactional
    public void recordOutbound(
            CostItemType itemType, Long itemId, BigDecimal qty, String refType, Long refId) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return;
        }
        BigDecimal unit = runningWacService.applyOutbound(itemType, itemId, qty);

        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isEmpty()) {
            log.debug("WAC running ok, no OPEN period to mirror {} #{} ({}/{})", itemType, itemId, refType, refId);
            return;
        }
        CostPeriod period = open.get();
        assertMutable(period);
        CostItemBalance row = getOrCreateForUpdate(period, itemType, itemId);
        row.applyOutbound(qty, unit);
        balanceRepository.save(row);
    }

    private CostItemBalance getOrCreateForUpdate(CostPeriod period, CostItemType type, Long itemId) {
        Optional<CostItemBalance> existing =
                balanceRepository.findForUpdate(period.getId(), type, itemId);
        if (existing.isPresent()) {
            return existing.get();
        }
        CostItemBalance created = CostItemBalance.opening(period, type, itemId, BigDecimal.ZERO, BigDecimal.ZERO);
        balanceRepository.saveAndFlush(created);
        return balanceRepository
                .findForUpdate(period.getId(), type, itemId)
                .orElse(created);
    }

    private static void assertMutable(CostPeriod period) {
        if (period.getStatus() == CostPeriodStatus.CLOSED) {
            throw ApiException.conflict("Kỳ đã khóa — không sửa phiếu; điều chỉnh ở kỳ sau");
        }
    }
}
