package com.hbsisms.feature.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort: add {@code payment.reconcile_flag} when ddl-auto=validate. */
@Component
@Order(21)
public class PaymentReconcileFlagColumnRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconcileFlagColumnRunner.class);

    private final JdbcTemplate jdbc;

    public PaymentReconcileFlagColumnRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select table_schema
                    from information_schema.columns
                    where table_name = 'payment' and column_name = 'reconcile_flag'
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
                                + "\".payment add column if not exists reconcile_flag varchar(20) not null default 'NONE'");
                log.info("Added {}.payment.reconcile_flag", safe);
            }
        } catch (Exception ex) {
            log.warn("Could not add payment.reconcile_flag: {}", ex.getMessage());
        }
    }
}
