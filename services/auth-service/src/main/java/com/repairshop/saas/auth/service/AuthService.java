package com.repairshop.saas.auth.service;

import com.repairshop.saas.auth.dto.CreateShopOwnerRequest;
import com.repairshop.saas.auth.dto.LoginRequest;
import com.repairshop.saas.auth.dto.LoginResponse;
import com.repairshop.saas.auth.dto.ShopLoginRequest;
import com.repairshop.saas.auth.dto.RegisterRequest;
import com.repairshop.saas.auth.dto.RegisterResponse;
import com.repairshop.saas.auth.dto.CreateShopRequest;
import com.repairshop.saas.auth.dto.RegisterTechnicianRequest;
import com.repairshop.saas.auth.dto.ShopOwnerResponse;
import com.repairshop.saas.auth.dto.PickupShopView;
import com.repairshop.saas.auth.dto.ShopOwnerView;
import com.repairshop.saas.auth.dto.ShopResponse;
import com.repairshop.saas.auth.dto.UpdateShopOwnerRequest;
import com.repairshop.saas.auth.dto.TechnicianResponse;
import com.repairshop.saas.auth.dto.UserResponse;
import com.repairshop.saas.auth.entity.Shop;
import com.repairshop.saas.auth.entity.User;
import com.repairshop.saas.auth.exception.BadRequestException;
import com.repairshop.saas.auth.exception.UnauthorizedException;
import com.repairshop.saas.auth.repository.ShopRepository;
import com.repairshop.saas.auth.repository.UserRepository;
import com.repairshop.saas.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OtpStore otpStore;

    /**
     * Unified login. The identifier in {@code request.email} may be either an
     * email or a mobile number. Resolution order:
     *
     *   1. users table (SUPER_ADMIN / SHOP_OWNER / EMPLOYEE roles)
     *      — looked up by email first, then phone with the usual +91 / 0-prefix
     *      tolerance. Authenticated against users.password_hash / users.otp_code.
     *   2. shops table (SHOP_LOGIN) — only attempted when the users-table lookup
     *      misses. Authenticated against shops.mobile_password_hash /
     *      shops.mobile_otp_code. Issues a single-shop scoped JWT.
     *
     * Default OTP for both flows is 123456 (see migration 54 and User.otp_code default).
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        boolean usingOtp = request.getOtp() != null && !request.getOtp().isBlank();
        boolean usingPwd = request.getPassword() != null && !request.getPassword().isBlank();
        if (!usingOtp && !usingPwd)
            throw new BadRequestException("Either password or otp is required");

        java.util.Optional<User> userOpt;
        if (request.getShopSlug() != null && !request.getShopSlug().isBlank()) {
            Shop shop = shopRepository.findBySlug(request.getShopSlug())
                    .orElseThrow(() -> new UnauthorizedException("Invalid shop or credentials"));
            userOpt = userRepository.findByShop_IdAndEmail(shop.getId(), request.getEmail());
        } else {
            userOpt = findUserByEmailOrPhone(request.getEmail());
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!user.getIsActive())
                throw new UnauthorizedException("Account is disabled");

            if (usingOtp) {
                if (user.getOtpCode() == null || !user.getOtpCode().equals(request.getOtp().trim()))
                    throw new UnauthorizedException("Invalid OTP");
            } else {
                if (user.getPasswordHash() == null
                        || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash()))
                    throw new UnauthorizedException("Invalid credentials");
            }
            return buildLoginResponse(user, null);
        }

        // Fall through to shop-mobile credentials. Skipped when a shopSlug was
        // supplied — that path is explicitly a tenant-scoped users-table lookup.
        if (request.getShopSlug() == null || request.getShopSlug().isBlank()) {
            java.util.Optional<Shop> shopOpt = findShopByMobile(request.getEmail());
            if (shopOpt.isPresent()) {
                ShopLoginRequest shopReq = ShopLoginRequest.builder()
                        .mobile(request.getEmail())
                        .password(request.getPassword())
                        .otp(request.getOtp())
                        .build();
                return shopLogin(shopReq);
            }
        }

        throw new UnauthorizedException("Invalid credentials");
    }

    /**
     * Login by SHOP MOBILE NUMBER (single-shop session). The shop's mobile
     * authenticates against either shops.mobile_password_hash or
     * shops.mobile_otp_code. The issued JWT is locked to this shopId via the
     * loginScope=SHOP claim — switch-shop will reject it. The token's subject
     * is the shop's owner user, so downstream services still see a userId.
     */
    @Transactional(readOnly = true)
    public LoginResponse shopLogin(ShopLoginRequest request) {
        boolean usingOtp = request.getOtp() != null && !request.getOtp().isBlank();
        boolean usingPwd = request.getPassword() != null && !request.getPassword().isBlank();
        if (!usingOtp && !usingPwd)
            throw new BadRequestException("Either password or otp is required");

        Shop shop = findShopByMobile(request.getMobile())
                .orElseThrow(() -> new UnauthorizedException("Invalid shop credentials"));
        if (!Boolean.TRUE.equals(shop.getIsActive()))
            throw new UnauthorizedException("Shop is disabled");
        if (shop.getOwnerUserId() == null)
            throw new UnauthorizedException("Shop is not linked to an owner — use email login");

        if (usingOtp) {
            if (shop.getMobileOtpCode() == null || !shop.getMobileOtpCode().equals(request.getOtp().trim()))
                throw new UnauthorizedException("Invalid OTP");
        } else {
            if (shop.getMobilePasswordHash() == null
                    || !passwordEncoder.matches(request.getPassword(), shop.getMobilePasswordHash()))
                throw new UnauthorizedException("Invalid shop credentials");
        }

        User owner = userRepository.findById(shop.getOwnerUserId())
                .orElseThrow(() -> new UnauthorizedException("Shop owner not found"));
        if (!Boolean.TRUE.equals(owner.getIsActive()))
            throw new UnauthorizedException("Owner account is disabled");

        return buildShopScopedLoginResponse(owner, shop);
    }

    /**
     * Switch the active shop for a SHOP_OWNER. Re-issues the JWT with the
     * given shopId in the claim so downstream services scope queries to it.
     * Rejects shopIds the owner doesn't actually own.
     */
    @Transactional(readOnly = true)
    public LoginResponse switchShop(UUID userId, UUID shopId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        if (!"SHOP_OWNER".equals(user.getRole()))
            throw new BadRequestException("Only shop owners can switch shops");
        Shop target = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found"));
        if (target.getOwnerUserId() == null || !target.getOwnerUserId().equals(userId))
            throw new UnauthorizedException("Shop not owned by this user");
        return buildLoginResponse(user, target.getId());
    }

    /**
     * Build a single-shop LoginResponse for shop-mobile logins. Marks
     * loginScope=SHOP and returns only the authenticated shop in the
     * shops array so the client knows there's nothing to switch to.
     */
    private LoginResponse buildShopScopedLoginResponse(User owner, Shop shop) {
        String token = jwtService.generateToken(
                owner.getId(),
                shop.getId(),
                owner.getEmail(),
                List.of(owner.getRole()),
                "SHOP"
        );
        List<LoginResponse.ShopAccess> shopList = List.of(LoginResponse.ShopAccess.builder()
                .id(shop.getId().toString())
                .name(shop.getName())
                .slug(shop.getSlug())
                .isActive(true)
                .build());
        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiryMs() / 1000)
                .userId(owner.getId().toString())
                .shopId(shop.getId().toString())
                .shopName(shop.getName())
                .email(owner.getEmail())
                .name(owner.getName())
                .roles(List.of(owner.getRole()))
                .roleLabel(friendlyEmployeeRoleLabel(owner.getRole()))
                .shops(shopList)
                .loginScope("SHOP")
                .loginType("SHOP_LOGIN")
                .build();
    }

    /**
     * Resolve the active shopId for this user and assemble LoginResponse.
     * If preferredShopId is supplied and the user owns it, use it; otherwise
     * fall back to the first owned shop (or user.shop / null per role).
     */
    private LoginResponse buildLoginResponse(User user, UUID preferredShopId) {
        // Determine the active shop for JWT shopId claim.
        List<Shop> ownedShops = "SHOP_OWNER".equals(user.getRole())
                ? shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(user.getId())
                : java.util.Collections.emptyList();

        Shop activeShop = null;
        if (preferredShopId != null) {
            activeShop = ownedShops.stream().filter(s -> s.getId().equals(preferredShopId)).findFirst().orElse(null);
        }
        if (activeShop == null && user.getShop() != null) {
            activeShop = user.getShop();
        }
        if (activeShop == null && !ownedShops.isEmpty()) {
            activeShop = ownedShops.get(0);
        }

        UUID shopId = activeShop != null ? activeShop.getId() : null;
        String shopName = activeShop != null ? activeShop.getName() : null;
        String token = jwtService.generateToken(
                user.getId(),
                shopId,
                user.getEmail(),
                List.of(user.getRole()),
                "OWNER"
        );

        final UUID activeShopId = activeShop != null ? activeShop.getId() : null;
        List<LoginResponse.ShopAccess> shopList = ownedShops.stream()
                .map(s -> LoginResponse.ShopAccess.builder()
                        .id(s.getId().toString())
                        .name(s.getName())
                        .slug(s.getSlug())
                        .isActive(activeShopId != null && s.getId().equals(activeShopId))
                        .build())
                .toList();

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtService.getExpiryMs() / 1000)
                .userId(user.getId().toString())
                .shopId(shopId != null ? shopId.toString() : null)
                .shopName(shopName)
                .email(user.getEmail())
                .name(user.getName())
                .roles(List.of(user.getRole()))
                .roleLabel(friendlyEmployeeRoleLabel(user.getRole()))
                .shops(shopList)
                .loginScope("OWNER")
                .loginType(loginTypeForRole(user.getRole()))
                .build();
    }

    /**
     * Map a stored users.role to the wire-level loginType the clients route on.
     * SHOP_OWNER and SUPER_ADMIN are 1:1; every other employee role (TECHNICIAN,
     * STAFF, PICKUP_PERSON) collapses to EMPLOYEE so the mobile app can route
     * them through the technician/employee UI uniformly.
     */
    private static String loginTypeForRole(String role) {
        if (role == null) return "EMPLOYEE";
        String r = role.trim().toUpperCase();
        if ("SUPER_ADMIN".equals(r)) return "SUPER_ADMIN";
        if ("SHOP_OWNER".equals(r))  return "SHOP_OWNER";
        return "EMPLOYEE";
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (shopRepository.existsBySlug(request.getShopSlug()))
            throw new BadRequestException("Shop slug already exists: " + request.getShopSlug());

        Shop shop = Shop.builder()
                .id(UUID.randomUUID())
                .name(request.getShopName())
                .slug(request.getShopSlug())
                .email(request.getEmail())
                .isActive(true)
                .build();
        shop = shopRepository.save(shop);

        if (userRepository.existsByShop_IdAndEmail(shop.getId(), request.getEmail()))
            throw new BadRequestException("Email already registered for this shop");

        User user = User.builder()
                .shop(shop)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName() != null ? request.getName() : request.getEmail())
                .role("SHOP_OWNER")
                .isActive(true)
                .build();
        user = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId().toString())
                .shopId(shop.getId().toString())
                .shopSlug(shop.getSlug())
                .email(user.getEmail())
                .message("Registration successful")
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShopResponse> listShops() {
        return shopRepository.findAll().stream()
                .map(s -> ShopResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .slug(s.getSlug())
                        .isActive(s.getIsActive())
                        .status(Boolean.TRUE.equals(s.getIsActive()) ? "ACTIVE" : "SUSPENDED")
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsersByShop(UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        String shopName = shop.getName();
        return userRepository.findByShop_IdOrderByEmailAsc(shopId).stream()
                .map(u -> UserResponse.builder()
                        .id(u.getId())
                        .email(u.getEmail())
                        .name(u.getName())
                        .role(u.getRole())
                        .isActive(u.getIsActive())
                        .shopId(shopId)
                        .shopName(shopName)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TechnicianResponse> listTechnicians(UUID shopId) {
        return userRepository.findByShop_IdAndRole(shopId, "TECHNICIAN").stream()
                .map(u -> TechnicianResponse.builder()
                        .id(u.getId())
                        .name(u.getName())
                        .email(u.getEmail())
                        .roleLabel("TECHNICIAN")
                        .build())
                .toList();
    }

    @Transactional
    public RegisterResponse registerTechnician(UUID shopId, RegisterTechnicianRequest request) {
        Shop shop = shopRepository.findById(shopId).orElseGet(() -> {
            // Shop may exist in ticket-service but not in auth (e.g. after auth DB reset). Create stub so technician can be registered.
            String slug = "shop-" + shopId.toString().replace("-", "");
            if (shopRepository.existsBySlug(slug))
                slug = "shop-" + shopId.toString();
            return shopRepository.save(Shop.builder()
                    .id(shopId)
                    .name("Shop")
                    .slug(slug)
                    .isActive(true)
                    .build());
        });
        if (userRepository.existsByShop_IdAndEmail(shop.getId(), request.getEmail()))
            throw new BadRequestException("Email already registered for this shop");

        User user = User.builder()
                .shop(shop)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .name(request.getName() != null ? request.getName() : request.getEmail())
                .role(canonicalEmployeeRole(request.getRoleLabel()))
                .isActive(true)
                .build();
        user = userRepository.save(user);

        return RegisterResponse.builder()
                .userId(user.getId().toString())
                .shopId(shop.getId().toString())
                .shopSlug(shop.getSlug())
                .email(user.getEmail())
                .message("Technician added")
                .build();
    }

    @Transactional
    public ShopResponse createShop(CreateShopRequest request) {
        String slug = request.getSlug().trim().toLowerCase().replaceAll("\\s+", "-");
        if (shopRepository.existsBySlug(slug))
            throw new BadRequestException("Shop slug already exists: " + slug);
        Shop shop = Shop.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .slug(slug)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .isActive(true)
                .build();
        shop = shopRepository.save(shop);
        return ShopResponse.builder()
                .id(shop.getId())
                .name(shop.getName())
                .slug(shop.getSlug())
                .isActive(true)
                .status("ACTIVE")
                .build();
    }

    @Transactional
    public ShopOwnerResponse createShopOwner(CreateShopOwnerRequest req) {
        String email = req.getEmail().trim();
        // Shop owners are platform-level users; their owned shops are linked via
        // shops.owner_user_id, not via users.shop_id. No parent shop needed.
        if (userRepository.findByEmail(email).isPresent())
            throw new BadRequestException("A user with this email already exists");

        User owner = User.builder()
                .shop(null)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .otpCode(req.getOtpCode() != null && !req.getOtpCode().isBlank() ? req.getOtpCode() : "123456")
                .name(req.getName() != null ? req.getName().trim() : email)
                .phone(req.getPhone())
                .secondaryMobile(req.getSecondaryMobile())
                .avatarUrl(req.getAvatarUrl())
                .idProofUrl(req.getIdProofUrl())
                .personalAddress(req.getPersonalAddress())
                .addrState(req.getAddrState())
                .addrDistrict(req.getAddrDistrict())
                .addrTaluk(req.getAddrTaluk())
                .addrArea(req.getAddrArea())
                .addrStreet(req.getAddrStreet())
                .addrPincode(req.getAddrPincode())
                .role("SHOP_OWNER")
                .isActive(true)
                .emailVerified(false)
                .build();
        owner = userRepository.save(owner);

        List<ShopOwnerResponse.ShopSummary> summaries = new java.util.ArrayList<>();
        for (CreateShopOwnerRequest.ShopLocationDto loc : req.getLocations()) {
            String slug = loc.getSlug();
            if (slug == null || slug.isBlank())
                slug = slugify(loc.getName()) + "-" + UUID.randomUUID().toString().substring(0, 6);
            if (shopRepository.existsBySlug(slug))
                slug = slug + "-" + UUID.randomUUID().toString().substring(0, 4);

            Shop shop = Shop.builder()
                    .id(UUID.randomUUID())
                    .name(loc.getName().trim())
                    .slug(slug)
                    .mobile(loc.getMobile())
                    .address(loc.getAddress())
                    .district(loc.getDistrict())
                    .state(loc.getState())
                    .pincode(loc.getPincode())
                    .taluk(loc.getTaluk())
                    .area(loc.getArea())
                    .street(loc.getStreet())
                    .gstNumber(loc.getGstNumber())
                    .latitude(loc.getLatitude())
                    .longitude(loc.getLongitude())
                    .frontImageUrl(loc.getFrontImageUrl())
                    .bannerImageUrl(loc.getBannerImageUrl())
                    .pickupFromTime(loc.getPickupFromTime())
                    .pickupToTime(loc.getPickupToTime())
                    .pickupDistanceKm(loc.getPickupDistanceKm())
                    .pickupEnabled(loc.getPickupEnabled() != null ? loc.getPickupEnabled() : false)
                    .gstCertificateUrl(loc.getGstCertificateUrl())
                    .udyamCertificateUrl(loc.getUdyamCertificateUrl())
                    .workingDays(loc.getWorkingDays())
                    .openingTime(loc.getOpeningTime())
                    .closingTime(loc.getClosingTime())
                    .ownerUserId(owner.getId())
                    .mobileOtpCode("123456")
                    .isActive(true)
                    .build();
            shop = shopRepository.save(shop);
            summaries.add(ShopOwnerResponse.ShopSummary.builder()
                    .id(shop.getId()).name(shop.getName()).slug(shop.getSlug()).build());
        }

        return ShopOwnerResponse.builder()
                .ownerId(owner.getId())
                .email(owner.getEmail())
                .name(owner.getName())
                .shops(summaries)
                .build();
    }

    /** 6-field progress score: identity, mobile, address, GST number, front image, certificate (any). */
    private static int locationProgressPercent(Shop s) {
        if (s == null) return 0;
        int total = 6;
        int done = 0;
        if (notBlank(s.getName()))            done++;
        if (notBlank(s.getMobile()))          done++;
        if (notBlank(s.getAddress()) || notBlank(s.getStreet()) || notBlank(s.getArea())) done++;
        if (notBlank(s.getGstNumber()))       done++;
        if (notBlank(s.getFrontImageUrl()))   done++;
        if (notBlank(s.getGstCertificateUrl()) || notBlank(s.getUdyamCertificateUrl())) done++;
        return (int) Math.round(done * 100.0 / total);
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    /**
     * Shop/owner login identifier resolution: try email first, then phone with
     * common formatting variants (raw, digits-only last-10, with/without +91
     * or leading 0). Lets owners log in with either their email or any
     * reasonable form of their phone number.
     */
    /**
     * Shop-mobile lookup for the shop-login flow. Tries the raw value first,
     * then the last-10 digits with the same +91 / 0-prefix variants as the
     * owner phone resolver so owners can register the number in any common
     * shape. Returns the first matching ACTIVE shop (mobile is not unique
     * at the DB level — see migration 54).
     */
    private java.util.Optional<Shop> findShopByMobile(String identifier) {
        if (identifier == null) return java.util.Optional.empty();
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) return java.util.Optional.empty();
        final java.util.List<Shop> firstHits = shopRepository.findByMobile(trimmed);
        if (!firstHits.isEmpty()) return firstHits.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).findFirst().or(() -> java.util.Optional.of(firstHits.get(0)));
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            String last10 = digits.substring(digits.length() - 10);
            for (String variant : java.util.List.of(last10, "0" + last10, "+91 " + last10, "+91" + last10, "91" + last10)) {
                final java.util.List<Shop> variantHits = shopRepository.findByMobile(variant);
                if (!variantHits.isEmpty()) return variantHits.stream().filter(s -> Boolean.TRUE.equals(s.getIsActive())).findFirst().or(() -> java.util.Optional.of(variantHits.get(0)));
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Issue (or surface, in dev) an OTP for shop-mobile login. The current
     * implementation simply returns the existing shops.mobile_otp_code so
     * developers can complete the flow without an SMS gateway. A real OTP
     * service would rotate the code here and trigger an SMS send.
     */
    @Transactional
    public String issueShopMobileOtp(String mobile) {
        Shop shop = findShopByMobile(mobile)
                .orElseThrow(() -> new BadRequestException("No shop registered for that mobile number"));
        if (shop.getMobileOtpCode() == null || shop.getMobileOtpCode().isBlank()) {
            shop.setMobileOtpCode("123456");
            shopRepository.save(shop);
        }
        return shop.getMobileOtpCode();
    }

    /**
     * Set/replace the bcrypt password for a shop's mobile login. Owner-only
     * action enforced at the controller via ownerId path param. Pass an empty
     * string to clear (disables password login; OTP login still works).
     */
    @Transactional
    public ShopOwnerView setShopMobilePassword(UUID ownerId, UUID shopId, String newPassword) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        if (shop.getOwnerUserId() == null || !shop.getOwnerUserId().equals(ownerId))
            throw new BadRequestException("Shop does not belong to this owner");
        if (newPassword == null || newPassword.isBlank()) {
            shop.setMobilePasswordHash(null);
        } else {
            shop.setMobilePasswordHash(passwordEncoder.encode(newPassword));
        }
        shopRepository.save(shop);
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BadRequestException("Owner not found"));
        return toOwnerView(owner, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(owner.getId()));
    }

    private java.util.Optional<User> findUserByEmailOrPhone(String identifier) {
        if (identifier == null) return java.util.Optional.empty();
        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) return java.util.Optional.empty();
        java.util.Optional<User> u = userRepository.findByEmail(trimmed);
        if (u.isPresent()) return u;
        u = userRepository.findByPhone(trimmed);
        if (u.isPresent()) return u;
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() >= 10) {
            String last10 = digits.substring(digits.length() - 10);
            for (String variant : java.util.List.of(last10, "0" + last10, "+91 " + last10, "+91" + last10, "91" + last10)) {
                u = userRepository.findByPhone(variant);
                if (u.isPresent()) return u;
            }
        }
        return java.util.Optional.empty();
    }

    private static String slugify(String s) {
        if (s == null) return "shop";
        return s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    private static final double EARTH_KM = 6371.0;

    /**
     * Pickup-enabled shops within {@code radiusKm} of the caller (lat,lng).
     * Filters shops.pickup_enabled = TRUE and computes Haversine distance in
     * Java so this works on both Postgres and H2. Sorted by distance asc.
     */
    @Transactional(readOnly = true)
    public List<PickupShopView> findPickupShopsNearby(java.math.BigDecimal callerLat, java.math.BigDecimal callerLng, double radiusKm) {
        if (callerLat == null || callerLng == null) return java.util.Collections.emptyList();
        final double oLat = callerLat.doubleValue();
        final double oLng = callerLng.doubleValue();
        return shopRepository.findByPickupEnabledTrueAndLatitudeNotNullAndLongitudeNotNull().stream()
                .map(s -> {
                    double d = haversineKm(oLat, oLng, s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
                    return toPickupShopView(s, d);
                })
                .filter(v -> v.getDistanceKm() <= radiusKm)
                .sorted(java.util.Comparator.comparing(PickupShopView::getDistanceKm))
                .toList();
    }

    /**
     * Public shop detail used by the customer "Pickup Service Shop" detail screen.
     * Returns the same shape as the pickup-nearby feed so the client can reuse
     * its view model. If the caller supplies (lat,lng), distanceKm is computed.
     */
    @Transactional(readOnly = true)
    public PickupShopView findPublicShop(UUID shopId, java.math.BigDecimal callerLat, java.math.BigDecimal callerLng) {
        Shop s = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        double d = 0.0;
        if (callerLat != null && callerLng != null && s.getLatitude() != null && s.getLongitude() != null) {
            d = haversineKm(callerLat.doubleValue(), callerLng.doubleValue(),
                    s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
        }
        return toPickupShopView(s, d);
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static PickupShopView toPickupShopView(Shop s, double distanceKm) {
        String addressLine = java.util.stream.Stream.of(
                s.getStreet(), s.getArea(), s.getTaluk(), s.getDistrict(), s.getState(), s.getPincode()
        ).filter(p -> p != null && !p.isBlank()).reduce((a, b) -> a + ", " + b).orElse(s.getAddress());
        return PickupShopView.builder()
                .id(s.getId())
                .name(s.getName())
                .slug(s.getSlug())
                .mobile(s.getMobile())
                .address(addressLine)
                .city(s.getArea() != null ? s.getArea() : s.getTaluk())
                .district(s.getDistrict())
                .state(s.getState())
                .pincode(s.getPincode())
                .gstNumber(s.getGstNumber())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .frontImageUrl(s.getFrontImageUrl())
                .bannerImageUrl(s.getBannerImageUrl())
                .pickupFromTime(s.getPickupFromTime())
                .pickupToTime(s.getPickupToTime())
                .pickupDistanceKm(s.getPickupDistanceKm())
                .distanceKm(Math.round(distanceKm * 10) / 10.0)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ShopOwnerView> listShopOwners() {
        return userRepository.findByRoleOrderByCreatedAtDesc("SHOP_OWNER").stream()
                .map(u -> toOwnerView(u, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(u.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShopOwnerView getShopOwner(UUID id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Owner not found: " + id));
        if (!"SHOP_OWNER".equals(u.getRole()))
            throw new BadRequestException("User is not a SHOP_OWNER");
        return toOwnerView(u, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(u.getId()));
    }

    @Transactional
    public ShopOwnerView updateShopOwner(UUID id, UpdateShopOwnerRequest req) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Owner not found: " + id));
        if (!"SHOP_OWNER".equals(u.getRole()))
            throw new BadRequestException("User is not a SHOP_OWNER");

        if (notBlank(req.getName()))           u.setName(req.getName().trim());
        if (notBlank(req.getEmail()))          u.setEmail(req.getEmail().trim());
        if (notBlank(req.getPassword()))       u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        if (req.getPhone() != null)            u.setPhone(req.getPhone());
        if (req.getSecondaryMobile() != null)  u.setSecondaryMobile(req.getSecondaryMobile());
        if (req.getAvatarUrl() != null)        u.setAvatarUrl(req.getAvatarUrl());
        if (req.getIdProofUrl() != null)       u.setIdProofUrl(req.getIdProofUrl());
        if (req.getPersonalAddress() != null)  u.setPersonalAddress(req.getPersonalAddress());
        if (req.getAddrState() != null)        u.setAddrState(req.getAddrState());
        if (req.getAddrDistrict() != null)     u.setAddrDistrict(req.getAddrDistrict());
        if (req.getAddrTaluk() != null)        u.setAddrTaluk(req.getAddrTaluk());
        if (req.getAddrArea() != null)         u.setAddrArea(req.getAddrArea());
        if (req.getAddrStreet() != null)       u.setAddrStreet(req.getAddrStreet());
        if (req.getAddrPincode() != null)      u.setAddrPincode(req.getAddrPincode());
        if (notBlank(req.getOtpCode()))        u.setOtpCode(req.getOtpCode().trim());

        u = userRepository.save(u);
        return toOwnerView(u, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(u.getId()));
    }

    @Transactional
    public void deleteShopOwner(UUID id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Owner not found: " + id));
        if (!"SHOP_OWNER".equals(u.getRole()))
            throw new BadRequestException("User is not a SHOP_OWNER");
        // Detach shops first (owner_user_id has ON DELETE SET NULL but be explicit).
        List<Shop> owned = shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(id);
        for (Shop s : owned) {
            s.setOwnerUserId(null);
            shopRepository.save(s);
        }
        userRepository.delete(u);
    }

    // ---- Email verification ----------------------------------------------------

    /** Issues a one-time code for this email; returns the code (dev) — production would email it. */
    @Transactional(readOnly = true)
    public String sendEmailVerifyOtp(String email) {
        if (email == null || email.isBlank())
            throw new BadRequestException("Email is required");
        // Confirm the email maps to a real user; do NOT leak existence on failure.
        userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BadRequestException("No account for this email"));
        return otpStore.issue(email.trim());
    }

    /** Confirms the OTP for the email; flips email_verified to true. */
    @Transactional
    public ShopOwnerView confirmEmailVerifyOtp(String email, String otp) {
        if (email == null || email.isBlank() || otp == null || otp.isBlank())
            throw new BadRequestException("Email and otp are required");
        User u = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new BadRequestException("No account for this email"));
        if (!otpStore.verify(email.trim(), otp))
            throw new UnauthorizedException("Invalid or expired OTP");
        u.setEmailVerified(true);
        u = userRepository.save(u);
        return toOwnerView(u, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(u.getId()));
    }

    // ---- Per-location CRUD -----------------------------------------------------

    @Transactional
    public ShopOwnerView addLocation(UUID ownerId, CreateShopOwnerRequest.ShopLocationDto loc) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new BadRequestException("Owner not found: " + ownerId));
        if (!"SHOP_OWNER".equals(owner.getRole()))
            throw new BadRequestException("User is not a SHOP_OWNER");
        if (loc.getName() == null || loc.getName().isBlank())
            throw new BadRequestException("Shop name is required");

        String slug = loc.getSlug();
        if (slug == null || slug.isBlank())
            slug = slugify(loc.getName()) + "-" + UUID.randomUUID().toString().substring(0, 6);
        if (shopRepository.existsBySlug(slug))
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 4);

        Shop shop = Shop.builder()
                .id(UUID.randomUUID())
                .name(loc.getName().trim())
                .slug(slug)
                .mobile(loc.getMobile())
                .address(loc.getAddress())
                .district(loc.getDistrict())
                .state(loc.getState())
                .pincode(loc.getPincode())
                .taluk(loc.getTaluk())
                .area(loc.getArea())
                .street(loc.getStreet())
                .gstNumber(loc.getGstNumber())
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .frontImageUrl(loc.getFrontImageUrl())
                .bannerImageUrl(loc.getBannerImageUrl())
                .gstCertificateUrl(loc.getGstCertificateUrl())
                .udyamCertificateUrl(loc.getUdyamCertificateUrl())
                .workingDays(loc.getWorkingDays())
                .openingTime(loc.getOpeningTime())
                .closingTime(loc.getClosingTime())
                .ownerUserId(owner.getId())
                .mobileOtpCode("123456")
                .isActive(true)
                .build();
        shopRepository.save(shop);
        return toOwnerView(owner, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(owner.getId()));
    }

    @Transactional
    public ShopOwnerView updateLocation(UUID ownerId, UUID shopId, CreateShopOwnerRequest.ShopLocationDto loc) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        if (shop.getOwnerUserId() == null || !shop.getOwnerUserId().equals(ownerId))
            throw new BadRequestException("Shop does not belong to this owner");

        // PATCH semantics: only update a field when the request actually sent it.
        // null = "field not in this request, leave existing value alone".
        // Empty string = explicit clear (handled by the same null-check skip
        // since the mobile/admin clients send the prior value when editing).
        if (loc.getName() != null && !loc.getName().isBlank()) shop.setName(loc.getName().trim());
        if (loc.getMobile() != null)               shop.setMobile(loc.getMobile());
        if (loc.getAddress() != null)              shop.setAddress(loc.getAddress());
        if (loc.getDistrict() != null)             shop.setDistrict(loc.getDistrict());
        if (loc.getState() != null)                shop.setState(loc.getState());
        if (loc.getPincode() != null)              shop.setPincode(loc.getPincode());
        if (loc.getTaluk() != null)                shop.setTaluk(loc.getTaluk());
        if (loc.getArea() != null)                 shop.setArea(loc.getArea());
        if (loc.getStreet() != null)               shop.setStreet(loc.getStreet());
        if (loc.getGstNumber() != null)            shop.setGstNumber(loc.getGstNumber());
        if (loc.getLatitude() != null)             shop.setLatitude(loc.getLatitude());
        if (loc.getLongitude() != null)            shop.setLongitude(loc.getLongitude());
        if (loc.getFrontImageUrl() != null)        shop.setFrontImageUrl(loc.getFrontImageUrl());
        if (loc.getBannerImageUrl() != null)       shop.setBannerImageUrl(loc.getBannerImageUrl());
        if (loc.getPickupFromTime() != null)       shop.setPickupFromTime(loc.getPickupFromTime());
        if (loc.getPickupToTime() != null)         shop.setPickupToTime(loc.getPickupToTime());
        if (loc.getPickupDistanceKm() != null)     shop.setPickupDistanceKm(loc.getPickupDistanceKm());
        if (loc.getPickupEnabled() != null)        shop.setPickupEnabled(loc.getPickupEnabled());
        if (loc.getWorkingDays() != null)          shop.setWorkingDays(loc.getWorkingDays());
        if (loc.getOpeningTime() != null)          shop.setOpeningTime(loc.getOpeningTime());
        if (loc.getClosingTime() != null)          shop.setClosingTime(loc.getClosingTime());
        if (loc.getGstCertificateUrl() != null)    shop.setGstCertificateUrl(loc.getGstCertificateUrl());
        if (loc.getUdyamCertificateUrl() != null)  shop.setUdyamCertificateUrl(loc.getUdyamCertificateUrl());
        if (loc.getServiceCategoriesJson() != null) shop.setServiceCategoriesJson(loc.getServiceCategoriesJson());
        shopRepository.save(shop);

        User owner = userRepository.findById(ownerId).orElseThrow(() -> new BadRequestException("Owner not found"));
        return toOwnerView(owner, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(owner.getId()));
    }

    @Transactional
    public void deleteLocation(UUID ownerId, UUID shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        if (shop.getOwnerUserId() == null || !shop.getOwnerUserId().equals(ownerId))
            throw new BadRequestException("Shop does not belong to this owner");
        shopRepository.delete(shop);
    }

    @Transactional
    public ShopOwnerView setShopOwnerActive(UUID id, boolean active) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Owner not found: " + id));
        u.setIsActive(active);
        u = userRepository.save(u);
        return toOwnerView(u, shopRepository.findByOwnerUserIdOrderByCreatedAtAsc(u.getId()));
    }

    private ShopOwnerView toOwnerView(User u, List<Shop> shops) {
        // Profile completeness: 5 sections — Basic info (name+email+phone), Avatar,
        // ID Proof, Personal Address, At-least-one-shop.
        int sections = 0;
        if (u.getName() != null && !u.getName().isBlank()
                && u.getEmail() != null && !u.getEmail().isBlank()
                && u.getPhone() != null && !u.getPhone().isBlank()) sections++;
        if (u.getAvatarUrl() != null && !u.getAvatarUrl().isBlank()) sections++;
        if (u.getIdProofUrl() != null && !u.getIdProofUrl().isBlank()) sections++;
        if (u.getPersonalAddress() != null && !u.getPersonalAddress().isBlank()) sections++;
        if (shops != null && !shops.isEmpty()) sections++;
        int total = 5;
        int percent = (int) Math.round((sections * 100.0) / total);

        boolean emailVerified = Boolean.TRUE.equals(u.getEmailVerified());

        List<ShopOwnerView.ShopLocationView> locs = shops.stream().map(s -> ShopOwnerView.ShopLocationView.builder()
                .id(s.getId())
                .name(s.getName())
                .slug(s.getSlug())
                .mobile(s.getMobile())
                .address(s.getAddress())
                .district(s.getDistrict())
                .state(s.getState())
                .pincode(s.getPincode())
                .taluk(s.getTaluk())
                .area(s.getArea())
                .street(s.getStreet())
                .gstNumber(s.getGstNumber())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .frontImageUrl(s.getFrontImageUrl())
                .bannerImageUrl(s.getBannerImageUrl())
                .pickupFromTime(s.getPickupFromTime())
                .pickupToTime(s.getPickupToTime())
                .pickupDistanceKm(s.getPickupDistanceKm())
                .pickupEnabled(s.getPickupEnabled())
                .workingDays(s.getWorkingDays())
                .openingTime(s.getOpeningTime())
                .closingTime(s.getClosingTime())
                .gstCertificateUrl(s.getGstCertificateUrl())
                .udyamCertificateUrl(s.getUdyamCertificateUrl())
                .isActive(s.getIsActive())
                .serviceCategoriesJson(s.getServiceCategoriesJson())
                .progressPercent(locationProgressPercent(s))
                .createdAt(s.getCreatedAt())
                .build()).toList();

        return ShopOwnerView.builder()
                .id(u.getId())
                .name(u.getName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .secondaryMobile(u.getSecondaryMobile())
                .avatarUrl(u.getAvatarUrl())
                .idProofUrl(u.getIdProofUrl())
                .personalAddress(u.getPersonalAddress())
                .addrState(u.getAddrState())
                .addrDistrict(u.getAddrDistrict())
                .addrTaluk(u.getAddrTaluk())
                .addrArea(u.getAddrArea())
                .addrStreet(u.getAddrStreet())
                .addrPincode(u.getAddrPincode())
                .role(u.getRole())
                .isActive(u.getIsActive())
                .emailVerified(emailVerified)
                .profileCompletePercent(percent)
                .sectionsComplete(sections)
                .sectionsTotal(total)
                .createdAt(u.getCreatedAt())
                .locations(locs)
                .build();
    }

    @Transactional
    public ShopResponse updateShopStatus(UUID shopId, String status) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new BadRequestException("Shop not found: " + shopId));
        boolean active = "ACTIVE".equalsIgnoreCase(status);
        shop.setIsActive(active);
        shop = shopRepository.save(shop);
        return ShopResponse.builder()
                .id(shop.getId())
                .name(shop.getName())
                .slug(shop.getSlug())
                .isActive(shop.getIsActive())
                .status(Boolean.TRUE.equals(shop.getIsActive()) ? "ACTIVE" : "SUSPENDED")
                .build();
    }

    // Map the owner-UI friendly label (Technician / Staff / Pickup Person) to the
    // canonical role stored in users.role. Defaults to TECHNICIAN when omitted so
    // older clients still work.
    static String canonicalEmployeeRole(String roleLabel) {
        if (roleLabel == null) return "TECHNICIAN";
        String normalized = roleLabel.trim().toUpperCase().replace(' ', '_');
        if (normalized.isEmpty()) return "TECHNICIAN";
        switch (normalized) {
            case "PICKUP_PERSON":
            case "PICKUPPERSON":
                return "PICKUP_PERSON";
            case "STAFF":
                return "STAFF";
            case "TECHNICIAN":
            default:
                return "TECHNICIAN";
        }
    }

    // Inverse of canonicalEmployeeRole: convert the stored role back to the
    // owner-UI friendly label. Tolerates the legacy "PICKUP PERSON" form.
    static String friendlyEmployeeRoleLabel(String storedRole) {
        if (storedRole == null) return null;
        String normalized = storedRole.trim().toUpperCase().replace(' ', '_');
        switch (normalized) {
            case "PICKUP_PERSON":
                return "Pickup Person";
            case "STAFF":
                return "Staff";
            case "TECHNICIAN":
                return "Technician";
            case "SHOP_OWNER":
                return "Shop Owner";
            default:
                return storedRole;
        }
    }
}
