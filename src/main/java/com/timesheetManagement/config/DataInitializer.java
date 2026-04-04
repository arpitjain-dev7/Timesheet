package com.timesheetManagement.config;

import com.timesheetManagement.constants.DefaultAdminConstants;
import com.timesheetManagement.entity.Role;
import com.timesheetManagement.entity.RoleName;
import com.timesheetManagement.entity.User;
import com.timesheetManagement.repository.RoleRepository;
import com.timesheetManagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // 1. Seed roles first (admin user depends on ROLE_ADMIN existing)
        seedRole(RoleName.ROLE_USER);
        seedRole(RoleName.ROLE_MANAGER);
        seedRole(RoleName.ROLE_ADMIN);

        // 2. Seed default admin user
        seedDefaultAdmin();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void seedRole(RoleName roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            roleRepository.save(new Role(null, roleName));
            log.info("Seeded role: {}", roleName);
        }
    }

    private void seedDefaultAdmin() {
        // Skip if the admin account already exists (idempotent)
        if (userRepository.existsByUsername(DefaultAdminConstants.USERNAME)) {
            log.debug("Default admin '{}' already exists — skipping seed.",
                      DefaultAdminConstants.USERNAME);
            return;
        }

        Role adminRole = roleRepository.findByName(DefaultAdminConstants.ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_ADMIN not found — cannot seed default admin"));

        User admin = User.builder()
                .firstName(DefaultAdminConstants.FIRST_NAME)
                .lastName(DefaultAdminConstants.LAST_NAME)
                .username(DefaultAdminConstants.USERNAME)
                .email(DefaultAdminConstants.EMAIL)
                .password(passwordEncoder.encode(DefaultAdminConstants.RAW_PASSWORD))
                .gender(DefaultAdminConstants.GENDER)
                .designation(DefaultAdminConstants.DESIGNATION)
                .location(DefaultAdminConstants.LOCATION)
                .typeOfEmployment(DefaultAdminConstants.EMPLOYMENT)
                .roles(Set.of(adminRole))
                .build();

        userRepository.save(admin);
        log.info("✅  Default admin user seeded  →  username='{}', email='{}'",
                 DefaultAdminConstants.USERNAME, DefaultAdminConstants.EMAIL);
    }
}
