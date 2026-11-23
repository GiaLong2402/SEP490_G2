package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/** Loại phát sinh sổ kho nguyên vật liệu. */
public enum RawLedgerEntryType {
    /** Nhập kho theo phiếu nhập. */
    IN,
    /** Xuất cho sản xuất (dòng 50). */
    OUT,
    /** Điều chỉnh / tồn đầu kỳ. */
    ADJUST;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
