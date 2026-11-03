package com.hbsisms.feature.role.dto;

import com.hbsisms.feature.role.AppRole;
import java.util.List;

public record RoleResponse(Short id, String code, String name, List<String> permissions) {

    public static RoleResponse from(AppRole role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getPermissions());
    }
}
