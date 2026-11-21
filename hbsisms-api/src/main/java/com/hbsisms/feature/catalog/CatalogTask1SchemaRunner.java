package com.hbsisms.feature.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort schema patch for catalog Task #1 when ddl-auto=validate: unit_of_measure table,
 * product flags + unit_id, raw_material.unit_id and FKs. Does not seed any unit rows.
 */
@Component
@Order(40)
public class CatalogTask1SchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogTask1SchemaRunner.class);

    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";

    private final JdbcTemplate jdbc;

    public CatalogTask1SchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec("unit_of_measure table",
                "create table if not exists " + T + "unit_of_measure ("
                        + "id bigserial primary key, "
                        + "code varchar(20) not null unique, "
                        + "name varchar(80) not null, "
                        + "is_active boolean not null default true)");
        exec("product.is_raw_material",
                "alter table " + T + "product add column if not exists is_raw_material boolean not null default false");
        exec("product.is_sellable",
                "alter table " + T + "product add column if not exists is_sellable boolean not null default true");
        exec("product.is_producible",
                "alter table " + T + "product add column if not exists is_producible boolean not null default false");
        exec("product.unit_id", "alter table " + T + "product add column if not exists unit_id bigint");
        exec("raw_material.unit_id", "alter table " + T + "raw_material add column if not exists unit_id bigint");
        addForeignKey("product", "fk_product_unit");
        addForeignKey("raw_material", "fk_raw_material_unit");
    }

    private void addForeignKey(String table, String constraint) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select 1
                    from information_schema.table_constraints
                    where constraint_schema = ? and table_name = ? and constraint_name = ?
                    """,
                    SCHEMA,
                    table,
                    constraint);
            if (!rows.isEmpty()) {
                return;
            }
            jdbc.execute("alter table " + T + "\"" + table + "\" add constraint " + constraint
                    + " foreign key (unit_id) references " + T + "unit_of_measure (id)");
            log.info("Added FK {}.{}", table, constraint);
        } catch (Exception ex) {
            log.warn("Could not add FK {}.{}: {}", table, constraint, ex.getMessage());
        }
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
