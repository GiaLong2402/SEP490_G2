package com.hbsisms.feature.analytics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort DDL for COGS snapshot columns when {@code ddl-auto=validate} (Inter 4 — Doanh).
 */
@Component
@Order(45)
public class AnalyticsSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSchemaRunner.class);
    private static final String T = "\"hbsisms\".";

    private final JdbcTemplate jdbc;

    public AnalyticsSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        alterAdd(
                "lot",
                "unit_cost",
                "alter table " + T + "lot add column if not exists unit_cost numeric(14,2)");
        alterAdd(
                "sales_order_line",
                "unit_cost",
                "alter table " + T + "sales_order_line add column if not exists unit_cost numeric(14,2)");
    }

    private void alterAdd(String table, String column, String sql) {
        try {
            jdbc.execute(sql);
            log.info("Ensured {}.{}", table, column);
        } catch (Exception ex) {
            log.warn("Could not ensure {}.{}: {}", table, column, ex.getMessage());
        }
    }
}
