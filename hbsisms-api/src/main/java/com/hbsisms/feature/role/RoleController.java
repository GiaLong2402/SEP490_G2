package com.hbsisms.feature.role;

import com.hbsisms.feature.role.dto.RoleResponse;
import com.hbsisms.feature.role.dto.UpdateRolePermissionsRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/roles")
@PreAuthorize("hasAuthority('admin')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<RoleResponse> list() {
        return roleService.listStaffRoles();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updatePermissions(
            @PathVariable Short id, @Valid @RequestBody UpdateRolePermissionsRequest request) {
        roleService.updatePermissions(id, request.permissions());
        return ResponseEntity.noContent().build();
    }
}
