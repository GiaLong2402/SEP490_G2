package com.hbsisms.feature.inventory;

import com.hbsisms.common.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/**
 * Quy tắc số lượng NVL: đơn vị khối lượng/thể tích cho phép thập phân; đơn vị đếm cái
 * (túi, khay, cuộn…) chỉ nhận số nguyên.
 */
public final class RawQuantityRules {

    /** Khớp {@code BomExplosionService}: kg/g/l… = liên tục; còn lại = đếm cái. */
    private static final Set<String> CONTINUOUS_UNITS = Set.of("kg", "g", "gram", "l", "lit", "ml");

    private RawQuantityRules() {}

    public static boolean isContinuousUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        return CONTINUOUS_UNITS.contains(unit.trim().toLowerCase(Locale.ROOT));
    }

    /** Túi, cái, cuộn, khay… — không thập phân. */
    public static boolean isPieceUnit(String unit) {
        return unit != null && !unit.isBlank() && !isContinuousUnit(unit);
    }

    /**
     * Từ chối số thập phân với đơn vị đếm. {@code qty == 0} bỏ qua (dòng complete bỏ nhận).
     */
    public static void requireWholeIfPiece(String unit, BigDecimal qty, String materialName) {
        if (qty == null || qty.signum() == 0 || !isPieceUnit(unit)) {
            return;
        }
        if (hasFraction(qty)) {
            String who = materialName == null || materialName.isBlank() ? "Nguyên liệu" : materialName;
            throw ApiException.badRequest(
                    who + ": đơn vị đếm cái không được nhập số thập phân — nhập số nguyên");
        }
    }

    /** Có phần lẻ (vd. 23,446) — dùng remainder, không phụ thuộc scale cột DB. */
    public static boolean hasFraction(BigDecimal qty) {
        if (qty == null) {
            return false;
        }
        return qty.remainder(BigDecimal.ONE).signum() != 0;
    }

    /** Phần dùng được khi đếm cái: làm tròn xuống số nguyên (bỏ “bụi” thập phân trên lô cũ). */
    public static BigDecimal wholeUsable(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return qty.setScale(0, RoundingMode.DOWN);
    }
}
