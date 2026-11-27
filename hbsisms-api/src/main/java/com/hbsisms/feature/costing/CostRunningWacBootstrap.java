package com.hbsisms.feature.costing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Sau DDL: seed {@code cost_running_balance} từ kỳ / tồn lô nếu còn thiếu. */
@Component
@Order(47)
public class CostRunningWacBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CostRunningWacBootstrap.class);

    private final CostRunningWacService runningWacService;

    public CostRunningWacBootstrap(CostRunningWacService runningWacService) {
        this.runningWacService = runningWacService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int n = runningWacService.bootstrapMissing();
            if (n == 0) {
                log.debug("cost_running_balance bootstrap: nothing to seed");
            }
        } catch (Exception ex) {
            log.warn("cost_running_balance bootstrap skipped: {}", ex.getMessage());
        }
    }
}