package com.hbsisms.feature.user;

import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.security.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the first 'admin' account when the database has no user yet. Runs after {@code RoleSeeder}. */
@Slf4j
@Component
@Order(2)
public class AdminUserSeeder implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminPassword;

    public AdminUserSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (Strings.trimToNull(adminPassword) == null) {
            log.warn("No user exists and SEED_ADMIN_PASSWORD is empty; skipping admin account creation");
            return;
        }
        if (!PasswordPolicy.isAcceptable(adminPassword)) {
            throw new IllegalStateException("SEED_ADMIN_PASSWORD must be " + PasswordPolicy.MIN_LENGTH
                    + " characters to " + PasswordPolicy.MAX_BYTES + " bytes");
        }
        AppUser admin = new AppUser();
        admin.setUsername(ADMIN_USERNAME);
        admin.setFullName("Quản trị hệ thống");
        admin.setUserType(UserType.STAFF);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(roleRepository.findByCode(DefaultRoles.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Admin role must be seeded first")));
        userRepository.save(admin);
        log.info("Seeded initial '{}' account", ADMIN_USERNAME);
    }
}
