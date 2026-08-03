package com.repairshop.saas.ticket.service;

import com.repairshop.saas.ticket.dto.CustomerRequest;
import com.repairshop.saas.ticket.dto.CustomerResponse;
import com.repairshop.saas.ticket.entity.PlatformCustomerAddress;
import com.repairshop.saas.ticket.entity.PlatformCustomerUser;
import com.repairshop.saas.ticket.repository.PlatformCustomerAddressRepository;
import com.repairshop.saas.ticket.repository.PlatformCustomerUserRepository;
import com.repairshop.saas.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final int MAX_LIST_SIZE = 200;
    private static final int MAX_PLATFORM_RESULTS = 50;

    private final PlatformCustomerUserRepository platformCustomerUserRepository;
    private final PlatformCustomerAddressRepository platformCustomerAddressRepository;
    private final TicketRepository ticketRepository;

    /** Default dev OTP seeded on every shop-created customer_users row so the
     *  customer can log into the mobile app with mobile + 123456 without
     *  going through the full sign-up form. Auth-service overrides this when
     *  the customer triggers the real OTP flow. */
    private static final String DEFAULT_DEV_OTP = "123456";

    @Transactional
    public CustomerResponse create(UUID shopId, CustomerRequest request) {
        // Shop-side customer create now writes the platform tables directly:
        //   customer_users     ← name, mobile, email, dev OTP
        //   customer_addresses ← the raw address as the default home row
        // The legacy per-shop `customers` table is no longer inserted to.
        String name  = request.getName()    != null ? request.getName().trim()    : null;
        String email = request.getEmail()   != null && !request.getEmail().isBlank()
                ? request.getEmail().trim().toLowerCase() : null;
        String phone = normalizePhone(request.getPhone());
        String addr  = request.getAddress() != null ? request.getAddress().trim() : null;
        String idProofUrl = trimOrNull(request.getIdProofUrl());

        // If a customer_users row already exists for this mobile, reuse it
        // instead of failing on the UNIQUE constraint. Refresh the name/email
        // from the form so the shop can correct stale data.
        PlatformCustomerUser user = phone == null
                ? null
                : platformCustomerUserRepository.findByMobile(phone).orElse(null);
        if (user == null) {
            user = new PlatformCustomerUser();
            user.setFullName(name);
            user.setEmail(email);
            user.setMobile(phone);
            user.setIdProofUrl(idProofUrl);
            user.setIsActive(true);
            user.setOtpCode(DEFAULT_DEV_OTP);
        } else {
            if (name != null && !name.isBlank()) user.setFullName(name);
            if (email != null) user.setEmail(email);
            if (idProofUrl != null) user.setIdProofUrl(idProofUrl);
            if (user.getOtpCode() == null) user.setOtpCode(DEFAULT_DEV_OTP);
            user.setIsActive(true);
        }
        user = platformCustomerUserRepository.save(user);

        // Prefer the structured fields (addressLine / locality / city / state /
        // pincode) when the form provided them; fall back to the concatenated
        // `address` string otherwise. Only inserts a default "home" address
        // when one doesn't already exist for this customer.
        String addressLine = trimOrNull(request.getAddressLine());
        String locality    = trimOrNull(request.getLocality());
        String city        = trimOrNull(request.getCity());
        String state       = trimOrNull(request.getState());
        String pincode     = trimOrNull(request.getPincode());
        if (addressLine == null && addr != null && !addr.isBlank()) addressLine = addr;

        boolean anyAddressField = addressLine != null || locality != null
                || city != null || state != null || pincode != null;
        if (anyAddressField) {
            boolean hasDefault = platformCustomerAddressRepository
                    .findPreferred(user.getId())
                    .map(a -> Boolean.TRUE.equals(a.getIsDefault()))
                    .orElse(false);
            if (!hasDefault) {
                PlatformCustomerAddress a = new PlatformCustomerAddress();
                a.setCustomerUserId(user.getId());
                a.setAddressLine(addressLine);
                a.setLocality(locality);
                a.setCity(city);
                a.setState(state);
                a.setPincode(pincode);
                a.setIsDefault(true);
                platformCustomerAddressRepository.save(a);
            }
        }
        return toPlatformResponse(shopId, user);
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> search(UUID shopId, String q) {
        // Single source of truth: customer_users. The shop-scope filter is
        // gone — any active customer_users row matching the query is
        // returned. shopId is kept in the signature for API compatibility.
        String query = (q != null ? q.trim() : "");
        List<PlatformCustomerUser> users = query.isEmpty()
                ? platformCustomerUserRepository.findAll(PageRequest.of(0, MAX_LIST_SIZE)).getContent()
                : platformCustomerUserRepository.searchActive(query, PageRequest.of(0, MAX_PLATFORM_RESULTS));
        List<CustomerResponse> out = new ArrayList<>(users.size());
        for (PlatformCustomerUser u : users) {
            out.add(toPlatformResponse(shopId, u));
        }
        return out;
    }

    /**
     * Lookup a customer by exact mobile number against customer_users only.
     * Used by the owner New-Customer form so an existing person isn't
     * double-created. Returns empty (→ 204) when no row matches.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerResponse> lookupByMobile(UUID shopId, String mobile) {
        String phone = normalizePhone(mobile);
        if (phone == null) return Optional.empty();
        return platformCustomerUserRepository.findByMobile(phone)
                .or(() -> platformCustomerUserRepository.findByMobile(lastTenDigits(phone)))
                .map(u -> toPlatformResponse(shopId, u));
    }

    /**
     * Returns the existing platform customer row for the given id.
     * The old per-shop "materialization" no longer happens — there's a
     * single customer_users row that every shop references.
     */
    @Transactional(readOnly = true)
    public CustomerResponse linkPlatformUser(UUID shopId, UUID platformUserId) {
        if (platformUserId == null) {
            throw new IllegalArgumentException("platformUserId is required");
        }
        PlatformCustomerUser u = platformCustomerUserRepository.findById(platformUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Platform customer not found: " + platformUserId));
        return toPlatformResponse(shopId, u);
    }

    private static String joinAddress(PlatformCustomerAddress a) {
        if (a == null) return null;
        String joined = java.util.stream.Stream.of(
                        a.getAddressLine(), a.getLocality(), a.getCity(), a.getState(), a.getPincode())
                .filter(s -> s != null && !s.isBlank())
                .reduce((x, y) -> x + ", " + y)
                .orElse(null);
        return joined;
    }

    private static String normalizePhone(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s+\\-]", "");
        if (s.length() > 10 && s.startsWith("91")) s = s.substring(s.length() - 10);
        return s.isEmpty() ? null : s;
    }

    private static String lastTenDigits(String phone) {
        if (phone == null || phone.length() <= 10) return phone;
        return phone.substring(phone.length() - 10);
    }

    // The old toResponse(Customer) helper was removed along with the
    // per-shop customers table; everything now flows through
    // toPlatformResponse(PlatformCustomerUser) below.

    private CustomerResponse toPlatformResponse(UUID shopId, PlatformCustomerUser u) {
        PlatformCustomerAddress addr = platformCustomerAddressRepository
                .findPreferred(u.getId()).orElse(null);
        long bookingCount = shopId == null ? 0L : ticketRepository.countByShopIdAndCustomerId(shopId, u.getId());
        Instant lastBookingAt = shopId == null ? null : ticketRepository
                .findFirstByShopIdAndCustomerIdOrderByCreatedAtDesc(shopId, u.getId())
                .map(t -> t.getCreatedAt())
                .orElse(null);
        CustomerResponse.CustomerResponseBuilder b = CustomerResponse.builder()
                .id(u.getId())
                .name(u.getFullName())
                .email(u.getEmail())
                .phone(u.getMobile())
                .mobile(u.getMobile())
                .idProofUrl(u.getIdProofUrl())
                .address(joinAddress(addr))
                .createdAt(null)
                .bookingCount(bookingCount)
                .lastBookingAt(lastBookingAt)
                .source("platform")
                .platformUserId(u.getId());
        applyAddress(b, addr);
        return b.build();
    }

    private static void applyAddress(CustomerResponse.CustomerResponseBuilder b, PlatformCustomerAddress a) {
        if (a == null) return;
        b.addressLine(a.getAddressLine())
                .locality(a.getLocality())
                .city(a.getCity())
                .district(a.getDistrict())
                .taluk(a.getTaluk())
                .area(a.getArea())
                .state(a.getState())
                .pincode(a.getPincode());
    }
}
