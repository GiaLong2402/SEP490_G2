package com.hbsisms.feature.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort: thêm {@code payment.purpose} khi ddl-auto=validate. */
@Component
@Order(21)
public class PaymentPurposeColumnRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentPurposeColumnRunner.class);

    private final JdbcTemplate jdbc;

    public PaymentPurposeColumnRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select table_schema
                    from information_schema.columns
                    where table_name = 'payment' and column_name = 'purpose'
                    """);
            if (!rows.isEmpty()) {
                return;
            }
            var paymentTables = jdbc.queryForList(
                    """
                    select table_schema
                    from information_schema.tables
                    where table_name = 'payment'
                    """);
            for (var row : paymentTables) {
                String schema = String.valueOf(row.get("table_schema"));
                if ("information_schema".equalsIgnoreCase(schema) || "pg_catalog".equalsIgnoreCase(schema)) {
                    continue;
                }
                String safe = schema.replace("\"", "");
                jdbc.execute(
                        "alter table \""
                                + safe
                                + "\".payment add column if not exists purpose varchar(20) not null default 'ORDER'");
                log.info("Added {}.payment.purpose", safe);
            }
        } catch (Exception ex) {
            log.warn("Could not add payment.purpose: {}", ex.getMessage());
        }
    }
}
