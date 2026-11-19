package com.hbsisms.feature.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort: add {@code version bigint not null default 0} (optimistic locking) when ddl-auto=validate. */
@Component
@Order(22)
public class OptimisticLockingSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockingSchemaRunner.class);

    private static final String SCHEMA = "hbsisms";
    private static final String[] TABLES = {"sales_order", "payment", "delivery", "approval_task"};

    private final JdbcTemplate jdbc;

    public OptimisticLockingSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String table : TABLES) {
            try {
                var rows = jdbc.queryForList(
                        """
                        select 1
                        from information_schema.columns
                        where table_schema = ? and table_name = ? and column_name = 'version'
                        """,
                        SCHEMA,
                        table);
                if (!rows.isEmpty()) {
                    continue;
                }
                jdbc.execute("alter table \"" + SCHEMA + "\".\"" + table
                        + "\" add column if not exists version bigint not null default 0");
                log.info("Added {}.{}.version", SCHEMA, table);
            } catch (Exception ex) {
                log.warn("Could not add {}.version: {}", table, ex.getMessage());
            }
        }
    }
}
