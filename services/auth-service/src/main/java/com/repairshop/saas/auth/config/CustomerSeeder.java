package com.repairshop.saas.auth.config;

import com.repairshop.saas.auth.entity.CustomerUser;
import com.repairshop.saas.auth.repository.CustomerUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the configured customer-app account so the mobile Customer login
 * works out of the box during dev. Idempotent — upserts password & OTP on
 * each run so credentials stay in sync with this file.
 *
 * Login on the mobile app's Customer tab:
 *   Mobile:   8939615914
 *   Password: nandha56@
 *   OTP:      562000
 */
@Component
@Profile("dev")
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class CustomerSeeder implements CommandLineRunner {

    public static final String SEED_MOBILE   = "8939615914";
    public static final String SEED_PASSWORD = "nandha56@";
    public static final String SEED_OTP      = "562000";
    public static final String SEED_NAME     = "Nandha";

    private final CustomerUserRepository customerUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        customerUserRepository.findByMobile(SEED_MOBILE).ifPresentOrElse(
                existing -> {
                    boolean dirty = false;
                    if (existing.getPasswordHash() == null
                            || !passwordEncoder.matches(SEED_PASSWORD, existing.getPasswordHash())) {
                        existing.setPasswordHash(passwordEncoder.encode(SEED_PASSWORD));
                        dirty = true;
                    }
                    if (existing.getOtpCode() == null || !SEED_OTP.equals(existing.getOtpCode())) {
                        existing.setOtpCode(SEED_OTP);
                        dirty = true;
                    }
                    if (!Boolean.TRUE.equals(existing.getIsActive())) {
                        existing.setIsActive(true);
                        dirty = true;
                    }
                    if (dirty) {
                        customerUserRepository.save(existing);
                        log.info("CustomerSeeder: refreshed credentials for {}", SEED_MOBILE);
                    }
                },
                () -> {
                    CustomerUser user = CustomerUser.builder()
                            .fullName(SEED_NAME)
                            .mobile(SEED_MOBILE)
                            .passwordHash(passwordEncoder.encode(SEED_PASSWORD))
                            .otpCode(SEED_OTP)
                            .isActive(true)
                            .build();
                    customerUserRepository.save(user);
                    log.info("CustomerSeeder: created customer (mobile={}, otp={}).",
                            SEED_MOBILE, SEED_OTP);
                }
        );
    }
}
