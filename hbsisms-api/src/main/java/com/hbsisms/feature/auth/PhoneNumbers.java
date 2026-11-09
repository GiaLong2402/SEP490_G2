package com.hbsisms.feature.auth;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import java.util.regex.Pattern;

/** Normalize VN mobile numbers the same way customer/staff phones are stored (0xxxxxxxxx). */
final class PhoneNumbers {

    private static final Pattern VN_MOBILE = Pattern.compile("^0\\d{9}$");

    private PhoneNumbers() {
    }

    static String normalizeVnMobile(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            throw ApiException.badRequest("Số điện thoại trên token không hợp lệ");
        }
        String digits = value.replaceAll("[\\s.-]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!VN_MOBILE.matcher(digits).matches()) {
            throw ApiException.badRequest("Số điện thoại trên token không hợp lệ");
        }
        return digits;
    }
}
