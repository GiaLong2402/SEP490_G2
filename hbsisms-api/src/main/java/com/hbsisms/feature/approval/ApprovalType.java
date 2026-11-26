package com.hbsisms.feature.approval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;

/** What an approval task is about. */
public enum ApprovalType {
    OVER_LIMIT_ORDER,
    /** Giảm giá / sửa giá tay trên đơn Sales — chờ Chủ duyệt trước khi hold. */
    ORDER_PRICE_OVERRIDE,
    DAMAGE_REPORT,
    PRICE_LIST,
    /** Bật mua nợ hoặc tăng hạn mức khách — task 72. */
    CREDIT_TERMS,
    STOCKTAKE,
    OTHER;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ApprovalType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(type -> type.code().equals(code.trim().toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loại duyệt không hợp lệ: " + code));
    }
}
