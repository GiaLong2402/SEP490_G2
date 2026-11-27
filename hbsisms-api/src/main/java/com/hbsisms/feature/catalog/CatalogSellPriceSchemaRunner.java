package com.hbsisms.feature.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Task 59 — cột % lời tùy chọn trên product / category khi ddl-auto=validate. */
@Component
@Order(47)
public class CatalogSellPriceSchemaRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSellPriceSchemaRunner.class);
    private static final String T = "\"hbsisms\".";

    private final JdbcTemplate jdbc;

    public CatalogSellPriceSchemaRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        alter("product", "sell_price_markup_percent", "smallint");
        alter("category", "sell_price_markup_percent", "smallint");
        ensureSellPriceHistoryTable();
    }

    private void alter(String table, String column, String type) {
        try {
            jdbc.execute(
                    "alter table " + T + table + " add column if not exists " + column + " " + type);
            log.info("Ensured {}.{}.{}", "hbsisms", table, column);
        } catch (Exception ex) {
            log.warn("Could not ensure {}.{}.{}: {}", "hbsisms", table, column, ex.getMessage());
        }
    }

    /** Bảng riêng cập giá bán theo giai đoạn (mỗi lần Áp dụng). */
    private void ensureSellPriceHistoryTable() {
        try {
            jdbc.execute(
                    "create table if not exists "
                            + T
                            + "sell_price_history ("
                            + "id bigserial primary key, "
                            + "variant_id bigint not null references "
                            + T
                            + "product_variant(id) on delete cascade, "
                            + "list_price numeric(14,2) not null, "
                            + "previous_list_price numeric(14,2), "
                            + "unit_cost numeric(14,2), "
                            + "cost_source varchar(20) not null, "
                            + "markup_percent integer not null, "
                            + "markup_source varchar(40), "
                            + "effective_from timestamptz not null, "
                            + "effective_to timestamptz, "
                            + "created_by_user_id bigint"
                            + ")");
            jdbc.execute(
                    "create index if not exists idx_sph_variant_from on "
                            + T
                            + "sell_price_history (variant_id, effective_from)");
            log.info("Ensured {}.sell_price_history", "hbsisms");
        } catch (Exception ex) {
            log.warn("Could not ensure {}.sell_price_history: {}", "hbsisms", ex.getMessage());
        }
    }
}
