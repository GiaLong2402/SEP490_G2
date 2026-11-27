package com.hbsisms.feature.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Khi API khởi động: đơn đặt trước kẹt {@code IN_PRODUCTION} sau nhập TP (allocate afterCommit lỗi)
 * được giữ hàng lại và chuyển Chờ tất toán + gửi thông báo nếu đã đủ.
 */
@Component
public class PreorderSettlementReconcileRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PreorderSettlementReconcileRunner.class);

    private final PreorderFulfillmentService preorderFulfillmentService;

    public PreorderSettlementReconcileRunner(PreorderFulfillmentService preorderFulfillmentService) {
        this.preorderFulfillmentService = preorderFulfillmentService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int toSettlement = preorderFulfillmentService.reconcileInProductionPreorders();
            if (toSettlement > 0) {
                log.info("Reconciled {} preorder(s) to AWAITING_SETTLEMENT on startup", toSettlement);
            }
            int toPreparing = preorderFulfillmentService.reconcileFullyPaidAwaitingPreparing();
            if (toPreparing > 0) {
                log.info("Reconciled {} preorder(s) to PREPARING on startup", toPreparing);
            }
        } catch (RuntimeException ex) {
            log.warn("Preorder settlement reconcile on startup failed: {}", ex.getMessage());
        }
    }
}
