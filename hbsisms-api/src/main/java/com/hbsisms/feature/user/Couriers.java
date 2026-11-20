package com.hbsisms.feature.user;

/**
 * Người giao hàng = tài khoản đang hoạt động có chức vụ {@value #POSITION}.
 * Mọi chỗ cần kiểm tra "là người giao" dùng lớp này (không dùng cờ riêng).
 */
public final class Couriers {

    public static final String POSITION = "Giao hàng";

    private Couriers() {}

    public static boolean isCourier(AppUser user) {
        return user != null && user.isActive() && POSITION.equals(user.getPosition());
    }
}
