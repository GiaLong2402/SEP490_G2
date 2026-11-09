package com.hbsisms.feature.sales;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link HoldExpireService} every minute (default). */
@Component
@ConditionalOnProperty(name = "app.sales.hold-expire-job.enabled", havingValue = "true", matchIfMissing = true)
public class HoldExpireScheduler {

    private final HoldExpireService holdExpireService;

    public HoldExpireScheduler(HoldExpireService holdExpireService) {
        this.holdExpireService = holdExpireService;
    }

    @Scheduled(cron = "${app.sales.hold-expire-job.cron:0 * * * * *}")
    public void tick() {
        holdExpireService.run();
    }
}
