package com.hbsisms.feature.customer;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sinh mã khách: B2C {@code KH-001}, B2B {@code KH-B001}.
 * Chỉ xét mã đúng pattern prefix + đúng 3 chữ số (bỏ qua {@code KH-KHOI-…}).
 */
public final class CustomerCodeGenerator {

    static final String B2C_PREFIX = "KH-";
    static final String B2B_PREFIX = "KH-B";

    private CustomerCodeGenerator() {
    }

    public static String prefixFor(CustomerType type) {
        return type == CustomerType.B2B ? B2B_PREFIX : B2C_PREFIX;
    }

    public static String nextCode(CustomerType type, List<String> existingCodes) {
        String prefix = prefixFor(type);
        Pattern pattern = Pattern.compile("^" + Pattern.quote(prefix) + "(\\d{3})$");
        int max = 0;
        for (String code : existingCodes) {
            if (code == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(code);
            if (matcher.matches()) {
                max = Math.max(max, Integer.parseInt(matcher.group(1)));
            }
        }
        return prefix + String.format("%03d", max + 1);
    }
}
