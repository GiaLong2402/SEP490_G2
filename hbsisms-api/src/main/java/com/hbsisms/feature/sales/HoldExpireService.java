package com.hbsisms.feature.sales;

import com.hbsisms.feature.payment.payos.PayosOrderExpireHandler;
import com.hbsisms.feature.sales.dto.HoldExpireJobResponse;
import com.hbsisms.feature.system.AppConfigService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment-hold expiry job (Excel #37): ask PayOS, then cancel unpaid orders past hold window.
 */
@Service
public class HoldExpireService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpireService.class);

    private final SalesOrderRepository salesOrderRepository;
    private final OrderEventRepository orderEventRepository;
    private final PayosOrderExpireHandler payosOrderExpireHandler;
    private final AppConfigService appConfigService;
    private final Clock clock;

    public HoldExpireService(
            SalesOrderRepository salesOrderRepository,
            OrderEventRepository orderEventRepository,
            PayosOrderExpireHandler payosOrderExpireHandler,
            AppConfigService appConfigService,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.orderEventRepository = orderEventRepository;
        this.payosOrderExpireHandler = payosOrderExpireHandler;
        this.appConfigService = appConfigService;
        this.clock = clock;
    }

    @Transactional
    public HoldExpireJobResponse run() {
        Instant now = Instant.now(clock);
        warnSalesHoldsExpiringSoon(now);

        // Chỉ xử lý đơn đã quá giờ giữ hàng + thời gian ân hạn (link PayOS còn nhận tiền trong lúc ân hạn).
        Instant cutoff = now.minusSeconds(appConfigService.paymentGraceMinutes() * 60L);
        List<SalesOrder> expired = salesOrderRepository.findExpiredHolds(cutoff);
        int processed = 0;
        for (SalesOrder header : expired) {
            salesOrderRepository.findByIdForUpdate(header.getId());
            SalesOrder detailed = salesOrderRepository
                    .findWithDetailsById(header.getId())
                    .orElse(header);
            if (payosOrderExpireHandler.expireIfNeeded(detailed)) {
                processed++;
            }
        }
        if (processed > 0) {
            log.info("Hold-expire job resolved {} order(s) (paid, cancelled, or expired)", processed);
        }
        return new HoldExpireJobResponse(processed);
    }

    private void warnSalesHoldsExpiringSoon(Instant now) {
        int warnMinutes = appConfigService.salesHoldWarnMinutes();
        if (warnMinutes <= 0) {
            return;
        }
        Instant warnBefore = now.plusSeconds(warnMinutes * 60L);
        for (SalesOrder header : salesOrderRepository.findHoldsNeedingWarn(now, warnBefore)) {
            if (header.getChannel() == SaleChannel.WEB || header.getChannel() == SaleChannel.APP) {
                continue;
            }
            boolean alreadyWarned = orderEventRepository
                    .findByOrder_IdOrderByCreatedAtAscIdAsc(header.getId())
                    .stream()
                    .anyMatch(e -> "hold_expiring_soon".equals(e.getEventType()));
            if (alreadyWarned) {
                continue;
            }
            OrderEvent event = new OrderEvent();
            event.setOrder(header);
            event.setEventType("hold_expiring_soon");
            event.setDescription(
                    "Sắp hết hạn giữ hàng Sales (còn ≤ " + warnMinutes + " phút) — nhắc Sales xử lý");
            orderEventRepository.save(event);
        }
    }
}
