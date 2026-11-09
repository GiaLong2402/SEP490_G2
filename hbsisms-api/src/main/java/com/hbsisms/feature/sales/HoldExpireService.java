package com.hbsisms.feature.sales;

import com.hbsisms.feature.sales.dto.HoldExpireJobResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Releases expired online payment holds (default 15 minutes). */
@Service
public class HoldExpireService {

    private static final Logger log = LoggerFactory.getLogger(HoldExpireService.class);

    private final OrderAllocationService orderAllocationService;

    public HoldExpireService(OrderAllocationService orderAllocationService) {
        this.orderAllocationService = orderAllocationService;
    }

    public HoldExpireJobResponse run() {
        int released = orderAllocationService.releaseExpiredHolds();
        if (released > 0) {
            log.info("Released holds for {} expired unpaid order(s)", released);
        }
        return new HoldExpireJobResponse(released);
    }
}
