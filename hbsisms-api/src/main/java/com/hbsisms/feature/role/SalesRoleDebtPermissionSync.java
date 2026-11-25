package com.hbsisms.feature.role;

import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role sales cũ từng có {@code debt} (xem công nợ). Đồng bộ: bỏ debt, giữ thu/chi qua {@code orders}.
 */
@Component
@Order(25)
public class SalesRoleDebtPermissionSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesRoleDebtPermissionSync.class);

    private final RoleRepository roleRepository;

    public SalesRoleDebtPermissionSync(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        roleRepository.findByCode(DefaultRoles.SALES).ifPresent(role -> {
            if (!role.hasPermission(Permission.DEBT)) {
                return;
            }
            EnumSet<Permission> next = EnumSet.noneOf(Permission.class);
            for (String code : role.getPermissions()) {
                if (Permission.DEBT.code().equals(code)) {
                    continue;
                }
                try {
                    next.add(Permission.fromCode(code));
                } catch (IllegalArgumentException ignored) {
                    // bỏ mã lạ
                }
            }
            role.replacePermissions(next);
            roleRepository.save(role);
            log.info("Stripped debt from sales role permissions → {}", next);
        });
    }
}
