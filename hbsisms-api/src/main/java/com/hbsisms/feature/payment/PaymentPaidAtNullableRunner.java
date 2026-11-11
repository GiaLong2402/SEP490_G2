package com.hbsisms.feature.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort: relax {@code payment.paid_at} NOT NULL. Must not fail startup —
 * checkout stub also sets a non-null {@code paidAt} while status stays PENDING.
 */
@Component
@Order(20)
public class PaymentPaidAtNullableRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentPaidAtNullableRunner.class);

    private final JdbcTemplate jdbc;

    public PaymentPaidAtNullableRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select table_schema
                    from information_schema.columns
                    where table_name = 'payment' and column_name = 'paid_at'
                    """);
            if (rows.isEmpty()) {
                log.warn("No payment.paid_at column found — skip nullable ALTER");
                return;
            }
            for (var row : rows) {
                String schema = String.valueOf(row.get("table_schema"));
                jdbc.execute(
                        "alter table \"" + schema.replace("\"", "") + "\".payment alter column paid_at drop not null");
                log.info("Relaxed {}.payment.paid_at to nullable", schema);
            }
        } catch (Exception ex) {
            log.warn("Could not relax payment.paid_at (checkout stub uses non-null paidAt): {}", ex.getMessage());
        }
    }
}
