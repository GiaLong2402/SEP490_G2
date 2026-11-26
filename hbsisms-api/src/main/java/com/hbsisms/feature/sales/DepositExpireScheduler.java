package com.hbsisms.feature.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Hủy đơn đặt trước quá hạn trả cọc (mỗi phút). */
@Component
@ConditionalOnProperty(name = "app.sales.deposit-expire-job.enabled", havingValue = "true", matchIfMissing = true)
public class DepositExpireScheduler {

    private final PreorderFulfillmentService preorderFulfillmentService;

    public DepositExpireScheduler(PreorderFulfillmentService preorderFulfillmentService) {
        this.preorderFulfillmentService = preorderFulfillmentService;
    }

    @Scheduled(cron = "${app.sales.deposit-expire-job.cron:0 * * * * *}")
    public void tick() {
        preorderFulfillmentService.expireOverdueDeposits();
    }
}
