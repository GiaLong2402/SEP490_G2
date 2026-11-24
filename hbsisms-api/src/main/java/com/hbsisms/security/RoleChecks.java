package com.hbsisms.security;

import com.hbsisms.feature.role.DefaultRoles;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SpEL helpers: {@code @PreAuthorize("@roles.ownerOrAdmin()")}.
 * Owner và admin chung quyền cao (theo sơ đồ Inter); warehouse làm thao tác kho/xưởng.
 */
@Component("roles")
public class RoleChecks {

    public boolean ownerOrAdmin() {
        AuthUser user = currentUser();
        if (user == null || user.roleCode() == null) {
            return false;
        }
        String code = user.roleCode();
        return DefaultRoles.ADMIN.equals(code) || DefaultRoles.OWNER.equals(code);
    }

    /** Kho / xưởng: warehouse + owner + admin. */
    public boolean warehouseOps() {
        AuthUser user = currentUser();
        if (user == null || user.roleCode() == null) {
            return false;
        }
        if (ownerOrAdmin()) {
            return true;
        }
        return DefaultRoles.WAREHOUSE.equals(user.roleCode());
    }

    private static AuthUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            return null;
        }
        return user;
    }
}
