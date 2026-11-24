package com.hbsisms.feature.catalog;

import java.util.Locale;

/**
 * Nhận diện quy cách khay 1 kg / 0,5 kg (BOM chuẩn 1 kg; giá 0,5 kg = nửa giá 1 kg).
 */
public final class PackSizes {

    private PackSizes() {}

    public static boolean isOneKg(ProductVariant variant) {
        if (variant == null) {
            return false;
        }
        Integer g = variant.getUnitWeightG();
        if (g != null) {
            if (g == 1000) {
                return true;
            }
            if (g == 500) {
                return false;
            }
        }
        String label = normalize(variant.getPackLabel());
        if (label.isEmpty()) {
            return false;
        }
        if (isHalfLabel(label)) {
            return false;
        }
        return label.contains("1kg")
                || label.contains("1 kg")
                || label.contains("1000g")
                || label.matches(".*\\b1\\s*kg.*");
    }

    public static boolean isHalfKg(ProductVariant variant) {
        if (variant == null) {
            return false;
        }
        Integer g = variant.getUnitWeightG();
        if (g != null) {
            if (g == 500) {
                return true;
            }
            if (g == 1000) {
                return false;
            }
        }
        return isHalfLabel(normalize(variant.getPackLabel()));
    }

    private static boolean isHalfLabel(String label) {
        return label.contains("0,5")
                || label.contains("0.5")
                || label.contains("500g")
                || label.contains("1/2");
    }

    private static String normalize(String packLabel) {
        if (packLabel == null) {
            return "";
        }
        return packLabel.toLowerCase(Locale.ROOT).replace('\u00a0', ' ').trim();
    }
}
