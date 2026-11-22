package com.hbsisms.feature.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort DDL for product BOM lines when ddl-auto=validate. */
@Component
@Order(41)
public class CatalogBomSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogBomSchemaRunner.class);
    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";

    private final JdbcTemplate jdbc;

    public CatalogBomSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec(
                "product_bom_line table",
                "create table if not exists " + T + "product_bom_line ("
                        + "id bigserial primary key, "
                        + "product_id bigint not null references " + T + "product (id), "
                        + "raw_material_id bigint references " + T + "raw_material (id), "
                        + "component_product_id bigint references " + T + "product (id), "
                        + "qty numeric(14,4) not null, "
                        + "unit_id bigint not null references " + T + "unit_of_measure (id), "
                        + "waste_percent numeric(6,2), "
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
