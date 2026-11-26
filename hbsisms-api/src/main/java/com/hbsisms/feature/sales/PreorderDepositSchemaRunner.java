package com.hbsisms.feature.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort: cột cọc / đặt trước trên {@code sales_order} khi ddl-auto=validate. */
@Component
@Order(23)
public class PreorderDepositSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PreorderDepositSchemaRunner.class);
    private static final String SCHEMA = "hbsisms";

    private final JdbcTemplate jdbc;

    public PreorderDepositSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumn("is_preorder", "boolean not null default false");
        addColumn("deposit_amount", "numeric(14,2) not null default 0");
        addColumn("deposit_percent", "numeric(5,2) not null default 0");
        addColumn("deposit_due_at", "timestamptz null");
        addColumn("paid_amount", "numeric(14,2) not null default 0");
    }

    private void addColumn(String column, String ddlType) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select 1
                    from information_schema.columns
                    where table_schema = ? and table_name = 'sales_order' and column_name = ?
                    """,
                    SCHEMA,
                    column);
            if (!rows.isEmpty()) {
                return;
            }
            jdbc.execute(
                    "alter table \"" + SCHEMA + "\".sales_order add column if not exists " + column + " " + ddlType);
            log.info("Added {}.sales_order.{}", SCHEMA, column);
        } catch (Exception ex) {
            log.warn("Could not add sales_order.{}: {}", column, ex.getMessage());
        }
    }
}
