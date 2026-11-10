package com.hbsisms.feature.auth.dto;

import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.user.AppUser;
import java.util.List;

/** Matches the frontend's {@code CurrentUser}. */
public record CurrentUserResponse(
        Long id,
        String username,
        String fullName,
        String email,
        String phone,
        String position,
        String roleCode,
        String roleName,
        List<String> permissions) {

    public static CurrentUserResponse from(AppUser user) {
        AppRole role = user.getRole();
        return new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getPosition(),
                role == null ? null : role.getCode(),
                role == null ? null : role.getName(),
                role == null ? List.of() : role.getPermissions());
    }
}
