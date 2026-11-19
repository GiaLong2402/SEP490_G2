package com.hbsisms.feature.approval;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Patch CHECK enum khi thêm {@link ApprovalType#ORDER_PRICE_OVERRIDE}. */
@Component
@Order(25)
public class ApprovalTypeConstraintPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ApprovalTypeConstraintPatcher.class);
    private static final String CONSTRAINT = "approval_task_approval_type_check";

    private final JdbcTemplate jdbc;

    public ApprovalTypeConstraintPatcher(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String allowed = Arrays.stream(ApprovalType.values())
                    .map(type -> "'" + type.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("alter table hbsisms.approval_task drop constraint if exists " + CONSTRAINT);
            jdbc.execute(
                    "alter table hbsisms.approval_task add constraint "
                            + CONSTRAINT
                            + " check (approval_type in ("
                            + allowed
                            + "))");
            log.info("Patched hbsisms.approval_task.approval_type check → {}", allowed);
        } catch (Exception ex) {
            log.warn("Could not patch approval_task.approval_type check: {}", ex.getMessage());
        }
    }
}
