package com.hbsisms.feature.role;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Inserts missing built-in roles on startup. Existing roles (and edited permissions) are left untouched. */
@Slf4j
@Component
@Order(1)
public class RoleSeeder implements ApplicationRunner {

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DefaultRoles.Definition definition : DefaultRoles.ALL) {
            if (!roleRepository.existsByCode(definition.code())) {
                roleRepository.save(new AppRole(definition.code(), definition.name(), definition.permissions()));
                log.info("Seeded role '{}'", definition.code());
            }
        }
    }
}
