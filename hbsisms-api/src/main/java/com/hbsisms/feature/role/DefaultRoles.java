package com.hbsisms.feature.role;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Built-in roles, mirroring database/seed.sql. */
public final class DefaultRoles {

    public static final String ADMIN = "admin";
    public static final String OWNER = "owner";
    public static final String SALES = "sales";
    public static final String WAREHOUSE = "warehouse";
    public static final String CUSTOMER = "customer";

    public static final List<Definition> ALL = List.of(
            new Definition(ADMIN, "Quản trị viên", EnumSet.allOf(Permission.class)),
            new Definition(OWNER, "Chủ cửa hàng", EnumSet.complementOf(EnumSet.of(Permission.ADMIN))),
            new Definition(SALES, "Nhân viên bán hàng",
                    EnumSet.of(Permission.ORDERS, Permission.CUSTOMERS, Permission.PRODUCTS, Permission.DEBT)),
            new Definition(WAREHOUSE, "Nhân viên kho", EnumSet.of(Permission.INVENTORY, Permission.PRODUCTS)),
            new Definition(CUSTOMER, "Khách hàng (B2C)", EnumSet.noneOf(Permission.class)));

    private DefaultRoles() {
    }

    public record Definition(String code, String name, Set<Permission> permissions) {
        public Definition {
            permissions = Set.copyOf(permissions);
        }
    }
}
