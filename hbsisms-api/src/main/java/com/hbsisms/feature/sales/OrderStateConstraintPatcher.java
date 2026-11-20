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

/** Patch CHECK enum state khi thêm {@link OrderState#EXPIRED}. */
@Component
@Order(24)
public class OrderStateConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderStateConstraintPatcher.class);
    private static final String CONSTRAINT = "sales_order_state_check";

    private final JdbcTemplate jdbc;

    public OrderStateConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowed = Arrays.stream(OrderState.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("alter table hbsisms.sales_order drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table hbsisms.sales_order add constraint "
                            + CONSTRAINT
                            + " check (state in ("
                            + allowed
                            + "))");
            log.info("Patched hbsisms.sales_order.state check → {}", allowed);
        } catch (Exception ex) {
            log.warn("Could not patch sales_order.state check: {}", ex.getMessage());
        }
    }
}
