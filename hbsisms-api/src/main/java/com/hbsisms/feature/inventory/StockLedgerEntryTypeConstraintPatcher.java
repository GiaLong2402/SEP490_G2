package com.hbsisms.feature.inventory;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Hibernate {@code ddl-auto=update} không mở rộng CHECK enum. DB cũ thiếu {@code RETURN} → nhận
 * hàng trả lỗi {@code stock_ledger_entry_type_check}.
 */
@Component
@Order(21)
public class StockLedgerEntryTypeConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StockLedgerEntryTypeConstraintPatcher.class);
    private static final String CONSTRAINT = "stock_ledger_entry_type_check";

    private final JdbcTemplate jdbc;

    public StockLedgerEntryTypeConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String allowed = Arrays.stream(LedgerEntryType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table hbsisms.stock_ledger drop constraint if exists " + CONSTRAINT);
        jdbc.execute(
                "alter table hbsisms.stock_ledger add constraint "
                        + CONSTRAINT
                        + " check (entry_type in ("
                        + allowed
                        + "))");
        log.info("Patched hbsisms.stock_ledger.entry_type check → {}", allowed);
    }
}
