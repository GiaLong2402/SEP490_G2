package com.hbsisms.feature.inventory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link LotExpireService} once a day (default 00:05 Asia/Ho_Chi_Minh). */
@Component
@ConditionalOnProperty(name = "app.inventory.expire-job.enabled", havingValue = "true", matchIfMissing = true)
public class LotExpireScheduler {

    private final LotExpireService lotExpireService;

    public LotExpireScheduler(LotExpireService lotExpireService) {
        this.lotExpireService = lotExpireService;
    }

    @Scheduled(
            cron = "${app.inventory.expire-job.cron:0 5 0 * * *}",
            zone = "${app.inventory.expire-job.zone:Asia/Ho_Chi_Minh}")
    public void dailyExpire() {
        lotExpireService.run();
    }
}
