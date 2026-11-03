package com.hbsisms.feature.user.dto;

import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserType;
import java.time.Instant;
import java.time.LocalDate;

/** Matches the frontend's {@code UserListItem}. Never exposes the password hash. */
public record UserResponse(
        Long id,
        String username,
        String email,
        String phone,
        String fullName,
        String avatarUrl,
        UserType userType,
        Short roleId,
        String roleName,
        String employeeCode,
        String position,
        LocalDate hiredAt,
        boolean isActive,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(AppUser user) {
        AppRole role = user.getRole();
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getUserType(),
                role == null ? null : role.getId(),
                role == null ? null : role.getName(),
                user.getEmployeeCode(),
                user.getPosition(),
                user.getHiredAt(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
