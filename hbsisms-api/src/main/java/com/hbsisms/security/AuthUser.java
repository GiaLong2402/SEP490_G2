package com.hbsisms.security;

import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.user.AppUser;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Authenticated principal. Authorities are the role's permission codes ("orders", "admin", ...),
 * so endpoints use {@code @PreAuthorize("hasAuthority('admin')")}.
 */
public record AuthUser(Long id, String username, String roleCode, Set<String> permissions) {

    public AuthUser {
        permissions = Set.copyOf(permissions);
    }

    public static AuthUser from(AppUser user) {
        AppRole role = user.getRole();
        return new AuthUser(
                user.getId(),
                user.getUsername(),
                role == null ? null : role.getCode(),
                role == null ? Set.of() : Set.copyOf(role.getPermissions()));
    }

    public List<GrantedAuthority> authorities() {
        return permissions.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }
}
