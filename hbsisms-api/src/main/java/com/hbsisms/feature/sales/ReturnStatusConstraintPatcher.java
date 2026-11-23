package com.hbsisms.feature.sales;

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
 * Hibernate {@code ddl-auto=update} không cập nhật CHECK enum khi thêm {@link ReturnStatus#DISCARDED}.
 */
@Component
@Order(21)
public class ReturnStatusConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReturnStatusConstraintPatcher.class);
    private static final String CONSTRAINT = "sales_return_status_check";

    private final JdbcTemplate jdbc;

    public ReturnStatusConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowed = Arrays.stream(ReturnStatus.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("alter table hbsisms.sales_return drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table hbsisms.sales_return add constraint "
                            + CONSTRAINT
                            + " check (status in ("
                            + allowed
                            + "))");
            log.info("Patched hbsisms.sales_return.status check → {}", allowed);
        } catch (Exception ex) {
            log.warn("Could not patch sales_return.status check: {}", ex.getMessage());
        }
    }
}
