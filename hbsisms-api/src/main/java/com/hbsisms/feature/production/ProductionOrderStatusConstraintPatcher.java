package com.hbsisms.feature.production;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto=update} không cập nhật CHECK enum khi thêm trạng thái mới cho lệnh sản xuất
 * ({@link ProductionOrderStatus#IN_PROGRESS}, {@link ProductionOrderStatus#AWAITING_RECEIPT}…). DB tạo từ khi enum
 * mới có DRAFT / APPROVED / CANCELLED nên xác nhận xuất NVL (lệnh → IN_PROGRESS) bị
 * {@code production_order_status_check} chặn. Dựng lại CHECK theo đúng enum mỗi lần khởi động.
 */
@Component
@Order(43)
public class ProductionOrderStatusConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrderStatusConstraintPatcher.class);
    private static final String CONSTRAINT = "production_order_status_check";

    private final JdbcTemplate jdbc;

    public ProductionOrderStatusConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowed = Arrays.stream(ProductionOrderStatus.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("alter table hbsisms.production_order drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table hbsisms.production_order add constraint "
                            + CONSTRAINT
                            + " check (status in ("
                            + allowed
                            + "))");
            log.info("Patched hbsisms.production_order.status check → {}", allowed);
        } catch (Exception ex) {
            log.warn("Could not patch production_order.status check: {}", ex.getMessage());
        }
    }
}
