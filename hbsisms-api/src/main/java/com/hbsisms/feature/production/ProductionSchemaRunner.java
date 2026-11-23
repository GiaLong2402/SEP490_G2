package com.hbsisms.feature.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort DDL for production order + material issue when ddl-auto=validate (Inter 1 — Duy). */
@Component
@Order(42)
public class ProductionSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSchemaRunner.class);
    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";

    private final JdbcTemplate jdbc;

    public ProductionSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        exec(
                "production_order table",
                "create table if not exists " + T + "production_order ("
                        + "id bigserial primary key, "
                        + "code varchar(30) not null unique, "
                        + "product_id bigint not null references " + T + "product (id), "
                        + "planned_qty numeric(14,4) not null, "
                        + "planned_due_date date, "
                        + "note text, "
                        + "estimated_production_cost numeric(14,2), "
                        + "estimated_material_cost numeric(14,2) not null default 0, "
                        + "status varchar(20) not null, "
                        + "created_by bigint references " + T + "app_user (id), "
                        + "approved_at timestamptz, "
                        + "approved_by bigint references " + T + "app_user (id), "
                        + "created_at timestamptz not null default now(), "
                        + "updated_at timestamptz not null default now())");
        exec(
                "production_order_material_line table",
                "create table if not exists " + T + "production_order_material_line ("
                        + "id bigserial primary key, "
                        + "production_order_id bigint not null references " + T + "production_order (id) on delete cascade, "
                        + "raw_material_id bigint not null references " + T + "raw_material (id), "
                        + "bom_qty_per_unit numeric(14,4) not null, "
                        + "waste_percent numeric(6,2), "
                        + "required_qty numeric(14,4) not null, "
                        + "on_hand_qty numeric(14,4) not null, "
                        + "sufficient boolean not null, "
                        + "line_estimated_cost numeric(14,2) default 0, "
                        + "sort_order int not null default 0)");
        exec(
                "material_issue table",
                "create table if not exists " + T + "material_issue ("
                        + "id bigserial primary key, "
                        + "code varchar(30) not null unique, "
                        + "production_order_id bigint not null unique references " + T + "production_order (id), "
                        + "status varchar(20) not null, "
                        + "created_by bigint references " + T + "app_user (id), "
                        + "completed_at timestamptz, "
                        + "completed_by bigint references " + T + "app_user (id), "
                        + "note text, "
                        + "created_at timestamptz not null default now(), "
                        + "updated_at timestamptz not null default now())");
        exec(
                "material_issue_line table",
                "create table if not exists " + T + "material_issue_line ("
                        + "id bigserial primary key, "
                        + "material_issue_id bigint not null references " + T + "material_issue (id) on delete cascade, "
                        + "raw_material_id bigint not null references " + T + "raw_material (id), "
                        + "qty_required numeric(14,4) not null, "
                        + "qty_issued numeric(14,4), "
                        + "raw_material_lot_id bigint references " + T + "raw_material_lot (id), "
                        + "sort_order int not null default 0)");
        // Task 50–51 (Cường): cột hoàn thành SX + PN thành phẩm.
        alterAdd("production_order", "actual_qty", "alter table " + T + "production_order add column if not exists actual_qty numeric(14,4)");
        alterAdd(
                "production_order",
                "actual_production_cost",
                "alter table " + T + "production_order add column if not exists actual_production_cost numeric(14,2)");
        alterAdd(
                "production_order",
                "actual_material_cost",
                "alter table " + T + "production_order add column if not exists actual_material_cost numeric(14,2)");
        alterAdd(
                "production_order",
                "finished_variant_id",
                "alter table " + T + "production_order add column if not exists finished_variant_id bigint");
        alterAdd(
                "production_order",
                "finished_receipt_id",
                "alter table " + T + "production_order add column if not exists finished_receipt_id bigint");
        alterAdd(
                "goods_receipt",
                "production_order_id",
                "alter table " + T + "goods_receipt add column if not exists production_order_id bigint");
    }

    private void alterAdd(String table, String column, String sql) {
        try {
            jdbc.execute(sql);
            log.info("Ensured {}.{}", table, column);
        } catch (Exception ex) {
            log.warn("Could not ensure {}.{}: {}", table, column, ex.getMessage());
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
