package com.hbsisms.feature.production;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Best-effort: {@code production_order.sales_order_id} khi ddl-auto=validate. */
@Component
@Order(23)
public class ProductionOrderSalesOrderColumnRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionOrderSalesOrderColumnRunner.class);
    private static final String SCHEMA = "hbsisms";

    private final JdbcTemplate jdbc;

    public ProductionOrderSalesOrderColumnRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            var rows = jdbc.queryForList(
                    """
                    select 1
                    from information_schema.columns
                    where table_schema = ? and table_name = 'production_order' and column_name = 'sales_order_id'
                    """,
                    SCHEMA);
            if (!rows.isEmpty()) {
                return;
            }
            jdbc.execute(
                    "alter table \"" + SCHEMA + "\".production_order add column if not exists sales_order_id bigint");
            jdbc.execute(
                    """
                    create index if not exists idx_production_order_sales_order
                    on "hbsisms".production_order (sales_order_id)
                    """);
            log.info("Added {}.production_order.sales_order_id", SCHEMA);
        } catch (Exception ex) {
            log.warn("Could not add production_order.sales_order_id: {}", ex.getMessage());
        }
    }
}
