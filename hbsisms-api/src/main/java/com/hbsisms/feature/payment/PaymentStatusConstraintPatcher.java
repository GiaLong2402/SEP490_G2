package com.hbsisms.feature.payment;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Patch CHECK {@code payment_status} khi thêm {@link PaymentStatus#DEPOSITED}. */
@Component
@Order(24)
public class PaymentStatusConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusConstraintPatcher.class);
    private static final String CONSTRAINT = "sales_order_payment_status_check";

    private final JdbcTemplate jdbc;

    public PaymentStatusConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowed = Arrays.stream(PaymentStatus.values())
                    .map(status -> "'" + status.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("alter table hbsisms.sales_order drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table hbsisms.sales_order add constraint "
                            + CONSTRAINT
                            + " check (payment_status in ("
                            + allowed
                            + "))");
            log.info("Patched hbsisms.sales_order.payment_status check → {}", allowed);
        } catch (Exception ex) {
            log.warn("Could not patch sales_order.payment_status check: {}", ex.getMessage());
        }
    }
}
