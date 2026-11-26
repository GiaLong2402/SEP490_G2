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
 * Hibernate {@code ddl-auto=update} không mở rộng CHECK enum và không bỏ NOT NULL. Chạy mỗi lần bật API (chỉ nới ràng buộc):
 * <ul>
 *   <li>{@code raw_material_ledger.entry_type} nhận thêm {@code DISCARD} (bỏ lô — dòng 54).</li>
 *   <li>{@code production_leftover.exp_date} cho phép trống: đồ khu khô trả về lô gốc, lô gốc có thể không có HSD.</li>
 * </ul>
 */
@Component
@Order(22)
public class RawLedgerSchemaPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RawLedgerSchemaPatcher.class);
    private static final String CONSTRAINT = "raw_material_ledger_entry_type_check";

    private final JdbcTemplate jdbc;

    public RawLedgerSchemaPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String allowed = Arrays.stream(RawLedgerEntryType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));
        jdbc.execute("alter table hbsisms.raw_material_ledger drop constraint if exists " + CONSTRAINT);
        jdbc.execute("alter table hbsisms.raw_material_ledger add constraint " + CONSTRAINT
                + " check (entry_type in (" + allowed + "))");
        jdbc.execute("alter table if exists hbsisms.production_leftover alter column exp_date drop not null");
        log.info("Patched raw_material_ledger.entry_type check → {}; production_leftover.exp_date nullable", allowed);
    }
}
