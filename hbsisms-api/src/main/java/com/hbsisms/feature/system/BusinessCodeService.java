package com.hbsisms.feature.system;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Sinh mã nghiệp vụ qua Postgres {@code nextval} — tránh race max+1 khi tạo song song.
 */
@Service
public class BusinessCodeService {

    public static final String SEQ_SALES_ORDER = "hbsisms.seq_sales_order";
    public static final String SEQ_SALES_REQUEST = "hbsisms.seq_sales_request";
    public static final String SEQ_PAYMENT = "hbsisms.seq_payment";
    public static final String SEQ_PAYOS_ORDER = "hbsisms.seq_payos_order_code";
    public static final String SEQ_DELIVERY = "hbsisms.seq_delivery";
    public static final String SEQ_RETURN = "hbsisms.seq_return_order";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BusinessCodeService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public String nextSalesOrderCode() {
        return formatYearly("SO", nextVal(SEQ_SALES_ORDER));
    }

    public String nextSalesRequestCode() {
        return formatYearly("YC", nextVal(SEQ_SALES_REQUEST));
    }

    public String nextPaymentCode() {
        return "TT-" + String.format(Locale.ROOT, "%04d", nextVal(SEQ_PAYMENT));
    }

    public long nextPayosOrderCode() {
        return nextVal(SEQ_PAYOS_ORDER);
    }

    public String nextDeliveryCode() {
        return formatYearly("DL", nextVal(SEQ_DELIVERY));
    }

    public String nextReturnCode() {
        return formatYearly("RET", nextVal(SEQ_RETURN));
    }

    private String formatYearly(String prefix, long n) {
        String year = LocalDate.now(clock).format(DateTimeFormatter.ofPattern("yyyy"));
        return prefix + "-" + year + "-" + String.format(Locale.ROOT, "%04d", n);
    }

    private long nextVal(String sequence) {
        Long value = jdbc.queryForObject("select nextval('" + sequence + "')", Long.class);
        return value == null ? 1L : value;
    }
}
