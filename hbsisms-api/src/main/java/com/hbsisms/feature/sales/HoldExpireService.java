package com.hbsisms.feature.sales;

import com.hbsisms.feature.payment.payos.PayosOrderExpireHandler;
import com.hbsisms.feature.sales.dto.HoldExpireJobResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Payment-hold expiry job (Excel #37): ask PayOS, then cancel unpaid orders past hold window.
 */
@Service
public class HoldExpireService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpireService.class);

    private final SalesOrderRepository salesOrderRepository;
    private final PayosOrderExpireHandler payosOrderExpireHandler;
    private final Clock clock;

    public HoldExpireService(
            SalesOrderRepository salesOrderRepository,
            PayosOrderExpireHandler payosOrderExpireHandler,
            Clock clock) {
        this.salesOrderRepository = salesOrderRepository;
        this.payosOrderExpireHandler = payosOrderExpireHandler;
        this.clock = clock;
    }

    public HoldExpireJobResponse run() {
        Instant now = Instant.now(clock);
        List<SalesOrder> expired = salesOrderRepository.findExpiredHolds(now);
        int processed = 0;
        for (SalesOrder header : expired) {
            SalesOrder detailed = salesOrderRepository
                    .findWithDetailsById(header.getId())
                    .orElse(header);
            if (payosOrderExpireHandler.expireIfNeeded(detailed)) {
                processed++;
            }
        }
        if (processed > 0) {
            log.info("Hold-expire job resolved {} order(s) (paid or cancelled)", processed);
        }
        return new HoldExpireJobResponse(processed);
    }
}
