package com.repairshop.saas.auth.config;

import com.repairshop.saas.auth.entity.User;
import com.repairshop.saas.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the platform super-admin operator accounts on startup. Super-admins
 * are platform-level (no parent shop) — they have users.shop_id = NULL.
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private static final String SUPER_ADMIN_1_EMAIL = "globogreenmobile@gmail.com";
    private static final String SUPER_ADMIN_1_PASSWORD = "Dhar@1254";
    private static final String SUPER_ADMIN_1_OTP = "801234";
    private static final String SUPER_ADMIN_2_EMAIL = "snandhadeveloper592000@gmail.com";
    private static final String SUPER_ADMIN_2_PASSWORD = "Nandha56@";
    private static final String SUPER_ADMIN_2_OTP = "592000";
    private static final String LEGACY_ADMIN_EMAIL = "barani";
    private static final String LEGACY_ADMIN_PASSWORD = "barani";
    private static final String LEGACY_ADMIN_OTP = "123456";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        upsertSuperAdmin(SUPER_ADMIN_1_EMAIL, SUPER_ADMIN_1_PASSWORD, SUPER_ADMIN_1_OTP, "Globo Green Mobile");
        upsertSuperAdmin(SUPER_ADMIN_2_EMAIL, SUPER_ADMIN_2_PASSWORD, SUPER_ADMIN_2_OTP, "Nandha Developer");
        upsertSuperAdmin(LEGACY_ADMIN_EMAIL,  LEGACY_ADMIN_PASSWORD,  LEGACY_ADMIN_OTP,  "Barani");
    }

    /** Idempotent upsert by email; super-admins are platform-level (shop=null). */
    private void upsertSuperAdmin(String email, String password, String otp, String name) {
        userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    boolean dirty = false;
                    if (existing.getPasswordHash() == null
                            || !passwordEncoder.matches(password, existing.getPasswordHash())) {
                        existing.setPasswordHash(passwordEncoder.encode(password));
                        dirty = true;
                    }
                    if (existing.getOtpCode() == null || !existing.getOtpCode().equals(otp)) {
                        existing.setOtpCode(otp);
                        dirty = true;
                    }
                    if (!"SUPER_ADMIN".equals(existing.getRole())) {
                        existing.setRole("SUPER_ADMIN");
                        dirty = true;
                    }
                    if (dirty) userRepository.save(existing);
                },
                () -> userRepository.save(User.builder()
                        .shop(null)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .otpCode(otp)
                        .name(name)
                        .role("SUPER_ADMIN")
                        .isActive(true)
                        .build())
        );
    }
}
