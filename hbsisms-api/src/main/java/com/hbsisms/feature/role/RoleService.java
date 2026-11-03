package com.hbsisms.feature.role;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.role.dto.RoleResponse;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    /** Roles assignable to staff accounts (the B2C customer role is excluded). */
    public List<RoleResponse> listStaffRoles() {
        return roleRepository.findAllByCodeNotOrderByIdAsc(DefaultRoles.CUSTOMER).stream()
                .map(RoleResponse::from)
                .toList();
    }

    /** A role that may be given to a staff account; the B2C customer role is rejected. */
    public AppRole getAssignableStaffRole(Short id) {
        return roleRepository.findById(id)
                .filter(RoleService::isStaffRole)
                .orElseThrow(() -> ApiException.badRequest("Vai trò không tồn tại"));
    }

    @Transactional
    public void updatePermissions(Short id, Collection<Permission> permissions) {
        AppRole role = roleRepository.findById(id)
                .filter(RoleService::isStaffRole)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy vai trò"));
        if (DefaultRoles.ADMIN.equals(role.getCode()) && !permissions.contains(Permission.ADMIN)) {
            throw ApiException.conflict("Vai trò Quản trị viên phải giữ quyền quản trị");
        }
        role.replacePermissions(permissions);
    }

    private static boolean isStaffRole(AppRole role) {
        return !DefaultRoles.CUSTOMER.equals(role.getCode());
    }
}
