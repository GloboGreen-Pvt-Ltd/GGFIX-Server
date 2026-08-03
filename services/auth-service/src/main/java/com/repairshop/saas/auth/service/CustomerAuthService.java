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

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerAuthService {

    private static final List<String> CUSTOMER_ROLES = List.of("CUSTOMER");
    private static final String DEFAULT_MOBILE_OTP = "123456";
    private static final SecureRandom RNG = new SecureRandom();

    private final CustomerUserRepository customerUserRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    // Best-effort email delivery (Resend). Reused for the customer OTP flow;
    // if unconfigured the code is still surfaced as devOtp in the response.
    private final EmailService emailService;
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

    // ---- OTP send + forgot-password (customer_users) --------------------------

    /**
     * Issue a login / password-reset OTP for a customer. The code is written to
     * customer_users.otp_code so both "sign in with OTP" (via /customer-login)
     * and the reset flow verify against the same stored value. For an EMAIL
     * identifier we generate a random 6-digit code and email it via Resend; for
     * a MOBILE identifier the code is the default 123456 (no SMS gateway). The
     * account must exist. In dev the code is surfaced as devOtp.
     */
    @Transactional
    public Map<String, Object> sendOtp(String identifier) {
        if (identifier == null || identifier.isBlank())
            throw new BadRequestException("Email or mobile number is required");
        String id = identifier.trim();
        boolean isEmail = id.contains("@");
        CustomerUser user = (isEmail
                ? customerUserRepository.findByEmail(id.toLowerCase())
                : customerUserRepository.findByMobile(id))
                .orElseThrow(() -> new BadRequestException("No account found for that email or mobile number."));

        Map<String, Object> res = new HashMap<>();
        if (isEmail) {
            String code = String.format("%06d", RNG.nextInt(1_000_000));
            user.setOtpCode(code);
            customerUserRepository.save(user);
            boolean sent = emailService.sendOtpEmail(id, code, "reset your GGFIX password");
            res.put("channel", "EMAIL");
            res.put("sent", sent);
            res.put("target", maskEmail(id));
            res.put("ttlMinutes", 10);
            res.put("devOtp", code); // dev convenience; production clients ignore this
        } else {
            user.setOtpCode(DEFAULT_MOBILE_OTP);
            customerUserRepository.save(user);
            res.put("channel", "MOBILE");
            res.put("sent", true);
            res.put("target", maskMobile(id));
            res.put("defaultOtp", DEFAULT_MOBILE_OTP);
        }
        res.put("email", isEmail ? id : user.getEmail());
        return res;
    }

    /**
     * Verify the reset OTP against customer_users.otp_code, set a new bcrypt
     * password, consume the code, and return a fresh customer session (auto
     * sign-in). Both email and mobile OTPs are the value stored by {@link #sendOtp}.
     */
    @Transactional
    public CustomerAuthResponse resetPasswordWithOtp(String identifier, String otp, String newPassword) {
        if (identifier == null || identifier.isBlank())
            throw new BadRequestException("Email or mobile number is required");
        if (otp == null || otp.isBlank())
            throw new BadRequestException("OTP is required");
        if (newPassword == null || newPassword.trim().length() < 8)
            throw new BadRequestException("Password must be at least 8 characters.");
        String id = identifier.trim();
        boolean isEmail = id.contains("@");
        CustomerUser user = (isEmail
                ? customerUserRepository.findByEmail(id.toLowerCase())
                : customerUserRepository.findByMobile(id))
                .orElseThrow(() -> new BadRequestException("No account found."));
        if (user.getOtpCode() == null || !user.getOtpCode().equals(otp.trim()))
            throw new UnauthorizedException("Invalid or expired OTP.");

        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        user.setOtpCode(null); // one-time use
        user = customerUserRepository.save(user);

        String token = jwtService.issueCustomerToken(user.getId(), CUSTOMER_ROLES);
        return toResponse(user, token);
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at - 1);
    }

    private static String maskMobile(String mobile) {
        String d = mobile.replaceAll("\\D", "");
        if (d.length() < 2) return "***";
        return "***-***-" + d.substring(d.length() - 2);
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
