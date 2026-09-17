package com.repairshop.saas.auth.service;

import com.repairshop.saas.auth.dto.CustomerAuthResponse;
import com.repairshop.saas.auth.dto.CustomerLoginRequest;
import com.repairshop.saas.auth.dto.CustomerRegisterRequest;
import com.repairshop.saas.auth.entity.CustomerUser;
import com.repairshop.saas.auth.exception.BadRequestException;
import com.repairshop.saas.auth.exception.UnauthorizedException;
import com.repairshop.saas.auth.repository.CustomerUserRepository;
import com.repairshop.saas.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthService {

    private static final List<String> CUSTOMER_ROLES = List.of("CUSTOMER");

    private final CustomerUserRepository customerUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    // Direct JDBC: customers + customer_user_addresses etc. live in tables
    // owned by sibling services. We only need a single UPDATE to walk-in
    // rows so a full JPA mapping would be overkill.
    private final JdbcTemplate jdbc;

    @Transactional
    public CustomerAuthResponse register(CustomerRegisterRequest request) {
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase()
                : null;

        if (mobile == null || mobile.isBlank())
            throw new BadRequestException("Mobile is required");
        if (customerUserRepository.existsByMobile(mobile))
            throw new BadRequestException("Mobile already registered: " + mobile);
        if (email != null && customerUserRepository.existsByEmail(email))
            throw new BadRequestException("Email already registered: " + email);

        CustomerUser user = CustomerUser.builder()
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .email(email)
                .mobile(mobile)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .build();
        user = customerUserRepository.save(user);

        // Auto-link any walk-in customers rows that match this mobile so the
        // shop's prior tickets for this person show up in My Orders immediately.
        linkExistingWalkInRows(user.getId(), mobile);

        String token = jwtService.issueCustomerToken(user.getId(), CUSTOMER_ROLES);
        return toResponse(user, token);
    }

    // UPDATE every customers row with this phone that has no platform_user_id
    // yet, pointing it at the new customer_users.id. Idempotent — re-runs are
    // a no-op once the rows are linked. Errors are swallowed (and logged) so
    // a missing customers table doesn't block sign-up in dev.
    private void linkExistingWalkInRows(UUID customerUserId, String mobile) {
        if (mobile == null || mobile.isBlank()) return;
        try {
            int updated = jdbc.update(
                    "UPDATE customers SET platform_user_id = ? "
                    + "WHERE phone = ? AND platform_user_id IS NULL",
                    customerUserId, mobile);
            if (updated > 0) {
                log.info("Linked {} walk-in customers row(s) to customer_users {} (mobile {})",
                        updated, customerUserId, mobile);
            }
        } catch (Exception e) {
            log.warn("Walk-in customers auto-link failed for mobile {}: {}", mobile, e.getMessage());
        }
    }

    @Transactional
    public CustomerAuthResponse login(CustomerLoginRequest request) {
        String mobile = request.getMobile() != null ? request.getMobile().trim() : null;
        String email = request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase()
                : null;

        if ((mobile == null || mobile.isBlank()) && (email == null || email.isBlank()))
            throw new BadRequestException("Either mobile or email is required");

        boolean usingOtp = request.getOtp() != null && !request.getOtp().isBlank();
        boolean usingPwd = request.getPassword() != null && !request.getPassword().isBlank();
        if (!usingOtp && !usingPwd)
            throw new BadRequestException("Either password or otp is required");

        CustomerUser user;
        if (mobile != null && !mobile.isBlank()) {
            user = customerUserRepository.findByMobile(mobile)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        } else {
            user = customerUserRepository.findByEmail(email)
                    .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        }

        if (!Boolean.TRUE.equals(user.getIsActive()))
            throw new UnauthorizedException("Account is disabled");

        if (usingOtp) {
            if (user.getOtpCode() == null || !user.getOtpCode().equals(request.getOtp().trim()))
                throw new UnauthorizedException("Invalid OTP");
        } else {
            if (user.getPasswordHash() == null
                    || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                throw new UnauthorizedException("Invalid credentials");
        }

        // Defensive heal: walk-in customers rows added after the user signed
        // up don't get linked at register time; pick them up on login too.
        linkExistingWalkInRows(user.getId(), user.getMobile());

        String token = jwtService.issueCustomerToken(user.getId(), CUSTOMER_ROLES);
        return toResponse(user, token);
    }

    @Transactional(readOnly = true)
    public CustomerAuthResponse me(UUID customerUserId) {
        CustomerUser user = customerUserRepository.findById(customerUserId)
                .orElseThrow(() -> new UnauthorizedException("Customer not found"));
        return toResponse(user, null);
    }

    private CustomerAuthResponse toResponse(CustomerUser user, String token) {
        return CustomerAuthResponse.builder()
                .accessToken(token)
                .userId(user.getId().toString())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mobile(user.getMobile())
                .roles(CUSTOMER_ROLES)
                .build();
    }
}
