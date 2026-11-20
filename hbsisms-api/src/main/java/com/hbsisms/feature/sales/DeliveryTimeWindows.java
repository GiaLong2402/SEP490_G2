package com.hbsisms.feature.sales;

import java.util.List;

/** 4 khung giờ giao / nhận hàng — dùng chung cho checkout web khách và tạo đợt giao nội bộ. */
public final class DeliveryTimeWindows {

    public static final List<String> ALL =
            List.of("08:00-10:00", "10:00-12:00", "14:00-16:00", "16:00-18:00");

    private DeliveryTimeWindows() {}

    public static boolean isValid(String window) {
        return window != null && ALL.contains(window);
    }
}
