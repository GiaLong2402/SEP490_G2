package com.hbsisms.feature.customer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Task 71 — khi {@code ddl-auto=validate}: thêm {@code credit_enabled} / {@code version},
 * nới {@code chk_b2b_terms}; tạo {@code payment_allocation} (1 lần thu → nhiều đơn).
 */
@Component
@Order(43)
public class DebtSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DebtSchemaRunner.class);

    private static final String SCHEMA = "hbsisms";
    private static final String T = "\"" + SCHEMA + "\".";
    private static final String TABLE = "customer";
    private static final String CONSTRAINT = "chk_b2b_terms";
    private static final String NEW_CHECK =
            "customer_type = 'B2B' OR credit_enabled OR (credit_limit = 0 AND net_days = 0)";

    private final JdbcTemplate jdbc;

    public DebtSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing(
                "credit_enabled",
                "alter table \"" + SCHEMA + "\".\"" + TABLE
                        + "\" add column if not exists credit_enabled boolean not null default false");
        addColumnIfMissing(
                "version",
                "alter table \"" + SCHEMA + "\".\"" + TABLE
                        + "\" add column if not exists version bigint not null default 0");
        backfillB2bCreditEnabled();
        patchChkB2bTerms();
        ensurePaymentAllocationTable();
        addColumnIfMissing(
                "pending_credit_enabled",
                "alter table \"" + SCHEMA + "\".\"" + TABLE
                        + "\" add column if not exists pending_credit_enabled boolean");
        addColumnIfMissing(
                "pending_credit_limit",
                "alter table \"" + SCHEMA + "\".\"" + TABLE
                        + "\" add column if not exists pending_credit_limit numeric(14,2)");
        addColumnIfMissing(
                "pending_net_days",
                "alter table \"" + SCHEMA + "\".\"" + TABLE
                        + "\" add column if not exists pending_net_days smallint");
    }

    private void ensurePaymentAllocationTable() {
        try {
            jdbc.execute(
                    "create table if not exists "
                            + T
                            + "payment_allocation ("
                            + "id bigserial primary key, "
                            + "payment_id bigint not null references "
                            + T
                            + "payment (id) on delete cascade, "
                            + "order_id bigint not null references "
                            + T
                            + "sales_order (id) on delete cascade, "
                            + "amount numeric(14,2) not null, "
                            + "created_at timestamptz not null default now(), "
                            + "constraint uk_alloc_payment_order unique (payment_id, order_id), "
                            + "constraint chk_alloc_amount_positive check (amount > 0))");
            jdbc.execute(
                    "create index if not exists idx_alloc_payment on "
                            + T
                            + "payment_allocation (payment_id)");
            jdbc.execute(
                    "create index if not exists idx_alloc_order on "
                            + T
                            + "payment_allocation (order_id)");
            log.info("Ensured {}.payment_allocation table", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not ensure payment_allocation: {}", ex.getMessage());
        }
    }

    private void addColumnIfMissing(String column, String ddl) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select 1
                    from information_schema.columns
                    where table_schema = ? and table_name = ? and column_name = ?
                    """,
                    SCHEMA,
                    TABLE,
                    column);
            if (!rows.isEmpty()) {
                return;
            }
            jdbc.execute(ddl);
            log.info("Added {}.{}.{}", SCHEMA, TABLE, column);
        } catch (Exception ex) {
            log.warn("Could not add {}.{}.{}: {}", SCHEMA, TABLE, column, ex.getMessage());
        }
    }

    private void backfillB2bCreditEnabled() {
        try {
            int updated = jdbc.update(
                    """
                    update "%s"."%s"
                    set credit_enabled = true
                    where customer_type = 'B2B' and credit_enabled = false
                    """
                            .formatted(SCHEMA, TABLE));
            if (updated > 0) {
                log.info("Backfilled credit_enabled=true for {} B2B customers", updated);
            }
        } catch (Exception ex) {
            log.warn("Could not backfill customer.credit_enabled: {}", ex.getMessage());
        }
    }

    private void patchChkB2bTerms() {
        try {
            jdbc.execute(
                    "alter table \"" + SCHEMA + "\".\"" + TABLE + "\" drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table \""
                            + SCHEMA
                            + "\".\""
                            + TABLE
                            + "\" add constraint "
                            + CONSTRAINT
                            + " check ("
                            + NEW_CHECK
                            + ")");
            log.info("Patched {}.{} → {}", TABLE, CONSTRAINT, NEW_CHECK);
        } catch (Exception ex) {
            log.warn("Could not patch {}.{}: {}", TABLE, CONSTRAINT, ex.getMessage());
        }
    }
}
