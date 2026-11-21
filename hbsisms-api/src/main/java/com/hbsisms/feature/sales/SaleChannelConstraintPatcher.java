package com.hbsisms.feature.sales;

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
 * Hibernate {@code ddl-auto=update} không cập nhật CHECK enum khi thêm giá trị mới.
 * DB tạo trước khi có {@link SaleChannel#FACEBOOK} → lưu kênh Facebook bị
 * {@code sales_order_channel_check} / {@code sales_request_channel_check}.
 */
@Component
@Order(21)
public class SaleChannelConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SaleChannelConstraintPatcher.class);

    private final JdbcTemplate jdbc;

    public SaleChannelConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String allowed = Arrays.stream(SaleChannel.values())
                .map(channel -> "'" + channel.name() + "'")
                .collect(Collectors.joining(", "));
        patch("hbsisms.sales_order", "sales_order_channel_check", allowed);
        patch("hbsisms.sales_request", "sales_request_channel_check", allowed);
    }

    private void patch(String table, String constraint, String allowed) {
        try {
            jdbc.execute("alter table " + table + " drop constraint if exists " + constraint);
            jdbc.execute(
                    "alter table "
                            + table
                            + " add constraint "
                            + constraint
                            + " check (channel in ("
                            + allowed
                            + "))");
            log.info("Patched {}.channel check → {}", table, allowed);
        } catch (Exception ex) {
            log.warn("Could not patch {}.channel check: {}", table, ex.getMessage());
        }
    }
}
