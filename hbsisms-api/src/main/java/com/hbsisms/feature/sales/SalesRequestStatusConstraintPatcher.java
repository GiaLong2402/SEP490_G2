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
 * DB cũ thiếu {@code REJECTED} → từ chối yêu cầu bị {@code sales_request_status_check}.
 */
@Component
@Order(20)
public class SalesRequestStatusConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesRequestStatusConstraintPatcher.class);
    private static final String CONSTRAINT = "sales_request_status_check";

    private final JdbcTemplate jdbc;

    public SalesRequestStatusConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        String allowed = Arrays.stream(RequestStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        // default_schema = hbsisms; JDBC raw SQL không kế thừa Hibernate schema.
        jdbc.execute("alter table hbsisms.sales_request drop constraint if exists " + CONSTRAINT);
        jdbc.execute(
                "alter table hbsisms.sales_request add constraint "
                        + CONSTRAINT
                        + " check (status in ("
                        + allowed
                        + "))");
        log.info("Patched hbsisms.sales_request.status check → {}", allowed);
    }
}
