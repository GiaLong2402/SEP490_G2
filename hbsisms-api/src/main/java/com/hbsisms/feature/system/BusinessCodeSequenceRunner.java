package com.hbsisms.feature.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort: tạo sequence mã nghiệp vụ khi ddl-auto=validate (Inter 3 — Duy).
 * PayOS order code bắt đầu từ 130001 để tránh đụng dải demo cũ.
 */
@Component
@Order(23)
public class BusinessCodeSequenceRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BusinessCodeSequenceRunner.class);

    private final JdbcTemplate jdbc;

    public BusinessCodeSequenceRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSequence(BusinessCodeService.SEQ_SALES_ORDER, 1);
        ensureSequence(BusinessCodeService.SEQ_SALES_REQUEST, 1);
        ensureSequence(BusinessCodeService.SEQ_PAYMENT, 1);
        ensureSequence(BusinessCodeService.SEQ_PAYOS_ORDER, 130001);
        ensureSequence(BusinessCodeService.SEQ_DELIVERY, 1);
        ensureSequence(BusinessCodeService.SEQ_RETURN, 1);
    }

    private void ensureSequence(String qualifiedName, long start) {
        try {
            jdbc.execute("create sequence if not exists " + qualifiedName + " start with " + start + " increment by 1");
            log.info("Ensured sequence {}", qualifiedName);
        } catch (Exception ex) {
            log.warn("Could not ensure sequence {}: {}", qualifiedName, ex.getMessage());
        }
    }
}
