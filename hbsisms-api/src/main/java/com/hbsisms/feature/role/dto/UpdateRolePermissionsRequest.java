package com.hbsisms.feature.role.dto;

import com.hbsisms.feature.role.Permission;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateRolePermissionsRequest(@NotNull(message = "Danh sách quyền là bắt buộc") List<@NotNull Permission> permissions) {
}
