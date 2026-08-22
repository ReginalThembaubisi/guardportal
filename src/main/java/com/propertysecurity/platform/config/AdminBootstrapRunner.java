package com.propertysecurity.platform.config;

import com.propertysecurity.platform.user.AppUser;
import com.propertysecurity.platform.user.AppUserRepository;
import com.propertysecurity.platform.user.Role;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Bootstraps the very first ADMIN account so there's a way in before any
 * staff exist. Only runs when ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD
 * / ADMIN_BOOTSTRAP_PHONE are set and no user with that email exists yet.
 * Safe to leave these env vars set permanently: it's a no-op once the
 * account is created.
 */
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = System.getenv("ADMIN_BOOTSTRAP_EMAIL");
        String password = System.getenv("ADMIN_BOOTSTRAP_PASSWORD");
        String phone = System.getenv("ADMIN_BOOTSTRAP_PHONE");

        if (email == null || password == null || phone == null) {
            return;
        }
        if (appUserRepository.existsByEmailAndDeletedAtIsNull(email)) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setFullName("Initial Admin");
        admin.setEmail(email);
        admin.setPhoneNumber(phone);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRoles(Set.of(Role.ADMIN));
        appUserRepository.save(admin);

        log.warn("Bootstrapped initial ADMIN account for {}. Log in and create further staff via /api/v1/staff.", email);
    }
}
