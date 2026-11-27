package com.hbsisms.feature.costing;

import com.hbsisms.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 56 — ghi nhận nhập/xuất vào số dư kỳ OPEN.
 * Nhập: giá trị thật (đơn giá nhập / giá vốn lệnh). Xuất: giá tạm = ĐG bình quân hiện hành
 * (hoặc ĐG kỳ trước nếu kỳ này chưa có đầu+nhập). Chuyển vị trí không gọi service này.
 */
@Service
public class CostingMovementService {

    private static final Logger log = LoggerFactory.getLogger(CostingMovementService.class);
    private static final int MONEY_SCALE = 2;

    private final CostPeriodRepository periodRepository;
    private final CostItemBalanceRepository balanceRepository;

    public CostingMovementService(
            CostPeriodRepository periodRepository, CostItemBalanceRepository balanceRepository) {
        this.periodRepository = periodRepository;
        this.balanceRepository = balanceRepository;
    }

    /**
     * Cộng nhập. Không có kỳ mở → bỏ qua (kho vẫn chạy). Kỳ CLOSED không bao giờ là OPEN.
     */
    @Transactional
    public void recordInbound(
            CostItemType itemType, Long itemId, BigDecimal qty, BigDecimal unitPrice, String refType, Long refId) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return;
        }
        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isEmpty()) {
            log.debug("WAC skip inbound {} #{} — chưa có kỳ mở ({}/{})", itemType, itemId, refType, refId);
            return;
        }
        CostPeriod period = open.get();
        assertMutable(period);
        CostItemBalance row = getOrCreateForUpdate(period, itemType, itemId);
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal value = qty.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        row.applyInbound(qty, value);
        balanceRepository.save(row);
    }

    /**
     * Cộng xuất với đơn giá tạm (WAC hiện hành). Không dùng giá lô vật lý.
     */
    @Transactional
    public void recordOutbound(
            CostItemType itemType, Long itemId, BigDecimal qty, String refType, Long refId) {
        if (itemId == null || qty == null || qty.signum() <= 0) {
            return;
        }
        Optional<CostPeriod> open = periodRepository.findByStatus(CostPeriodStatus.OPEN);
        if (open.isEmpty()) {
            log.debug("WAC skip outbound {} #{} — chưa có kỳ mở ({}/{})", itemType, itemId, refType, refId);
            return;
        }
        CostPeriod period = open.get();
        assertMutable(period);
        CostItemBalance row = getOrCreateForUpdate(period, itemType, itemId);
        BigDecimal unit = resolveProvisionalUnitCost(row, period);
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

    /** ĐG tạm = (đầu+nhập)/(SL đầu+SL nhập); nếu chưa có → ĐG kỳ trước đã khóa; không có → 0. */
    private BigDecimal resolveProvisionalUnitCost(CostItemBalance row, CostPeriod period) {
        row.refreshDerived();
        if (row.getAvgUnitCost() != null) {
            return row.getAvgUnitCost();
        }
        YearMonth ym = YearMonth.from(period.getPeriodStart());
        Optional<CostPeriod> prev = periodRepository.findByPeriodStart(ym.minusMonths(1).atDay(1));
        if (prev.isPresent() && prev.get().getStatus() == CostPeriodStatus.CLOSED) {
            return balanceRepository
                    .findByPeriod_IdAndItemTypeAndItemId(prev.get().getId(), row.getItemType(), row.getItemId())
                    .map(CostItemBalance::getAvgUnitCost)
                    .orElse(BigDecimal.ZERO);
        }
        return BigDecimal.ZERO;
    }

    private static void assertMutable(CostPeriod period) {
        if (period.getStatus() == CostPeriodStatus.CLOSED) {
            throw ApiException.conflict("Kỳ đã khóa — không sửa phiếu; điều chỉnh ở kỳ sau");
        }
    }
}
