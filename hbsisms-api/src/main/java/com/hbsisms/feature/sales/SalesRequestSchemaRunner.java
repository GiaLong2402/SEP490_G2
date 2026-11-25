package com.hbsisms.feature.sales;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** DDL best-effort cho {@code sales_request_line} khi ddl-auto=validate. */
@Component
@Order(43)
public class SalesRequestSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesRequestSchemaRunner.class);
    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";

    private final JdbcTemplate jdbc;

    public SalesRequestSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec(
                "sales_request_line table",
                "create table if not exists " + T + "sales_request_line ("
                        + "id bigserial primary key, "
                        + "sales_request_id bigint not null references " + T + "sales_request (id) on delete cascade, "
                        + "variant_id bigint not null references " + T + "product_variant (id), "
                        + "unit varchar(10) not null, "
                        + "qty int not null, "
                        + "qty_khay int not null, "
                        + "fulfillable_qty int, "
                        + "shortage_qty int, "
                        + "sort_order int not null default 0)");
    }

    private void exec(String what, String sql) {
        try {
            jdbc.execute(sql);
            log.info("Ensured {}", what);
        } catch (Exception ex) {
            log.warn("Could not ensure {}: {}", what, ex.getMessage());
        }
    }
}
