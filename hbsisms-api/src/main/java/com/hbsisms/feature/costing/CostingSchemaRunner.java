package com.hbsisms.feature.costing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Task 55 — khi {@code ddl-auto=validate}: tạo {@code cost_period} / {@code cost_item_balance}
 * và index đảm bảo chỉ 1 kỳ OPEN.
 */
@Component
@Order(46)
public class CostingSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CostingSchemaRunner.class);
    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";

    private final JdbcTemplate jdbc;

    public CostingSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureCostPeriodTable();
        ensureCostItemBalanceTable();
        ensureOneOpenPeriodIndex();
        ensureCostRunningBalanceTable();
    }

    /** WAC liên hoàn ngoài sổ kỳ — nguồn sự thật ĐG/GT/SL. */
    private void ensureCostRunningBalanceTable() {
        try {
            jdbc.execute(
                    "create table if not exists "
                            + T
                            + "cost_running_balance ("
                            + "id bigserial primary key, "
                            + "item_type varchar(20) not null, "
                            + "item_id bigint not null, "
                            + "qty_on_hand numeric(14,3) not null default 0, "
                            + "inventory_value numeric(14,2) not null default 0, "
                            + "avg_unit_cost numeric(14,2), "
                            + "updated_at timestamptz not null default now(), "
                            + "constraint uk_cost_run_item unique (item_type, item_id), "
                            + "constraint chk_cost_run_nonneg check ("
                            + "qty_on_hand >= 0 and inventory_value >= 0))");
            jdbc.execute(
                    "create index if not exists idx_cost_run_item on "
                            + T
                            + "cost_running_balance (item_type, item_id)");
            log.info("Ensured {}.cost_running_balance table", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not ensure cost_running_balance: {}", ex.getMessage());
        }
    }

    private void ensureCostPeriodTable() {
        try {
            jdbc.execute(
                    "create table if not exists "
                            + T
                            + "cost_period ("
                            + "id bigserial primary key, "
                            + "period_start date not null, "
                            + "status varchar(20) not null, "
                            + "opened_at timestamptz not null, "
                            + "opened_by_user_id bigint, "
                            + "closed_at timestamptz, "
                            + "closed_by_user_id bigint, "
                            + "reopen_reason varchar(500), "
                            + "constraint uk_cost_period_start unique (period_start))");
            jdbc.execute(
                    "create index if not exists idx_cost_period_status on " + T + "cost_period (status)");
            log.info("Ensured {}.cost_period table", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not ensure cost_period: {}", ex.getMessage());
        }
    }

    private void ensureCostItemBalanceTable() {
        try {
            jdbc.execute(
                    "create table if not exists "
                            + T
                            + "cost_item_balance ("
                            + "id bigserial primary key, "
                            + "period_id bigint not null references "
                            + T
                            + "cost_period (id) on delete cascade, "
                            + "item_type varchar(20) not null, "
                            + "item_id bigint not null, "
                            + "opening_qty numeric(14,3) not null default 0, "
                            + "opening_value numeric(14,2) not null default 0, "
                            + "inbound_qty numeric(14,3) not null default 0, "
                            + "inbound_value numeric(14,2) not null default 0, "
                            + "outbound_qty numeric(14,3) not null default 0, "
                            + "outbound_value numeric(14,2) not null default 0, "
                            + "closing_qty numeric(14,3) not null default 0, "
                            + "closing_value numeric(14,2) not null default 0, "
                            + "avg_unit_cost numeric(14,2), "
                            + "constraint uk_cost_bal_period_item unique (period_id, item_type, item_id), "
                            + "constraint chk_cost_bal_nonneg check ("
                            + "opening_qty >= 0 and opening_value >= 0 "
                            + "and inbound_qty >= 0 and inbound_value >= 0 "
                            + "and outbound_qty >= 0 and outbound_value >= 0 "
                            + "and closing_qty >= 0 and closing_value >= 0))");
            jdbc.execute(
                    "create index if not exists idx_cost_bal_period on "
                            + T
                            + "cost_item_balance (period_id)");
            jdbc.execute(
                    "create index if not exists idx_cost_bal_item on "
                            + T
                            + "cost_item_balance (item_type, item_id)");
            log.info("Ensured {}.cost_item_balance table", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not ensure cost_item_balance: {}", ex.getMessage());
        }
    }

    /** Partial unique index: tối đa một dòng {@code status = 'OPEN'}. */
    private void ensureOneOpenPeriodIndex() {
        try {
            jdbc.execute(
                    "create unique index if not exists uk_cost_period_one_open on "
                            + T
                            + "cost_period (status) where status = 'OPEN'");
            log.info("Ensured {}.uk_cost_period_one_open", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not ensure uk_cost_period_one_open: {}", ex.getMessage());
        }
    }
}
