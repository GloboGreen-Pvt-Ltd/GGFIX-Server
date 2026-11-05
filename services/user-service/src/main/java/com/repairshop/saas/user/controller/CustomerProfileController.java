package com.repairshop.saas.user.controller;

import com.repairshop.saas.user.dto.AddressRequest;
import com.repairshop.saas.user.dto.AddressResponse;
import com.repairshop.saas.user.dto.CustomerProfileResponse;
import com.repairshop.saas.user.dto.SavedDeviceRequest;
import com.repairshop.saas.user.dto.SavedDeviceResponse;
import com.repairshop.saas.user.dto.UpdateProfileRequest;
import com.repairshop.saas.user.entity.AddressEntry;
import com.repairshop.saas.user.entity.CustomerAddress;
import com.repairshop.saas.user.entity.CustomerSavedDevice;
import com.repairshop.saas.user.entity.CustomerUser;
import com.repairshop.saas.user.exception.ResourceNotFoundException;
import com.repairshop.saas.user.repository.CustomerAddressRepository;
import com.repairshop.saas.user.repository.CustomerSavedDeviceRepository;
import com.repairshop.saas.user.repository.CustomerUserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customer")
@RequiredArgsConstructor
@Tag(name = "Customer Profile", description = "Platform customer profile, addresses and saved devices")
@SecurityRequirement(name = "Bearer")
public class CustomerProfileController {

    private final CustomerUserRepository customerUserRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final CustomerSavedDeviceRepository customerSavedDeviceRepository;

    private UUID userIdFrom(HttpServletRequest request) {
        String uid = (String) request.getAttribute("userId");
        if (uid == null) throw new IllegalStateException("Missing user context");
        return UUID.fromString(uid);
    }

    /**
     * Returns the local CustomerUser mirror, creating a minimal stub if it
     * doesn't exist yet. The JWT (signed by auth-service) is the source of
     * truth that this user is valid — we just need a local row so foreign-key
     * constraints on addresses / saved-devices / orders are satisfied.
     *
     * Required in dev where each service runs its own in-memory H2 and so
     * doesn't share rows with auth-service; harmless in prod where the row
     * already exists.
     */
    private CustomerUser ensureLocalUser(UUID userId) {
        return customerUserRepository.findById(userId).orElseGet(() -> {
            CustomerUser stub = CustomerUser.builder()
                    .id(userId)
                    .isActive(true)
                    .build();
            return customerUserRepository.save(stub);
        });
    }

    // ---------------------------------------------------------------------
    // Profile
    // ---------------------------------------------------------------------

    @GetMapping("/profile")
    @Operation(summary = "Get own customer profile")
    public CustomerProfileResponse getProfile(HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerUser user = ensureLocalUser(userId);
        return toProfileResponse(user);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update own customer profile")
    public CustomerProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest body,
                                                 HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerUser user = ensureLocalUser(userId);
        if (body.getFullName() != null) user.setFullName(body.getFullName());
        if (body.getEmail() != null) user.setEmail(body.getEmail());
        if (body.getMobile() != null) user.setMobile(body.getMobile());
        if (body.getAlternateMobile() != null) user.setAlternateMobile(body.getAlternateMobile());
        if (body.getProfileImageUrl() != null) user.setProfileImageUrl(body.getProfileImageUrl());
        CustomerUser saved = customerUserRepository.save(user);
        return toProfileResponse(saved);
    }

    // ---------------------------------------------------------------------
    // Addresses
    // ---------------------------------------------------------------------

    @GetMapping("/addresses")
    @Operation(summary = "List own addresses")
    public List<AddressResponse> listAddresses(HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        return customerAddressRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toAddressResponse).toList();
    }

    @PostMapping("/addresses")
    @Operation(summary = "Create a new address")
    public AddressResponse createAddress(@Valid @RequestBody AddressRequest body,
                                         HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        // Make sure the local customer_users row exists so the FK on
        // customer_addresses.customer_user_id is satisfied.
        ensureLocalUser(userId);
        // The form now collects area + district + taluk explicitly. We dual-write:
        //   area     -> locality  (so legacy readers of `.locality` keep working)
        //   district -> city      (so legacy readers of `.city` keep working)
        // If the client also sends an explicit locality/city, those win.
        String resolvedCity = body.getCity() != null ? body.getCity() : body.getDistrict();
        String resolvedLocality = body.getLocality() != null ? body.getLocality() : body.getArea();
        CustomerAddress entity = CustomerAddress.builder()
                .customerUserId(userId)
                .label(body.getLabel() != null ? body.getLabel() : "Home")
                .fullName(body.getFullName())
                .mobile(body.getMobile())
                .pincode(body.getPincode())
                .locality(resolvedLocality)
                .area(body.getArea())
                .addressLine(body.getAddressLine())
                .city(resolvedCity)
                .district(body.getDistrict())
                .taluk(body.getTaluk())
                .state(body.getState())
                .latitude(body.getLatitude())
                .longitude(body.getLongitude())
                .isDefault(Boolean.TRUE.equals(body.getIsDefault()))
                .build();
        CustomerAddress saved = customerAddressRepository.save(entity);
        resyncAddressesJson(userId);
        return toAddressResponse(saved);
    }

    @PutMapping("/addresses/{id}")
    @Operation(summary = "Update one of own addresses")
    public AddressResponse updateAddress(@PathVariable UUID id,
                                         @Valid @RequestBody AddressRequest body,
                                         HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerAddress entity = customerAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        ensureOwnedByUser(entity.getCustomerUserId(), userId);
        if (body.getLabel() != null) entity.setLabel(body.getLabel());
        if (body.getFullName() != null) entity.setFullName(body.getFullName());
        if (body.getMobile() != null) entity.setMobile(body.getMobile());
        if (body.getPincode() != null) entity.setPincode(body.getPincode());
        if (body.getLocality() != null) entity.setLocality(body.getLocality());
        if (body.getArea() != null) {
            entity.setArea(body.getArea());
            // Mirror to legacy `locality` unless the caller also sent explicit locality.
            if (body.getLocality() == null) entity.setLocality(body.getArea());
        }
        if (body.getAddressLine() != null) entity.setAddressLine(body.getAddressLine());
        if (body.getCity() != null) entity.setCity(body.getCity());
        if (body.getDistrict() != null) {
            entity.setDistrict(body.getDistrict());
            // Mirror to legacy `city` column unless the caller also sent an explicit city.
            if (body.getCity() == null) entity.setCity(body.getDistrict());
        }
        if (body.getTaluk() != null) entity.setTaluk(body.getTaluk());
        if (body.getState() != null) entity.setState(body.getState());
        if (body.getLatitude() != null) entity.setLatitude(body.getLatitude());
        if (body.getLongitude() != null) entity.setLongitude(body.getLongitude());
        if (body.getIsDefault() != null) entity.setIsDefault(body.getIsDefault());
        CustomerAddress saved = customerAddressRepository.save(entity);
        resyncAddressesJson(userId);
        return toAddressResponse(saved);
    }

    @DeleteMapping("/addresses/{id}")
    @Operation(summary = "Delete one of own addresses")
    public ResponseEntity<Void> deleteAddress(@PathVariable UUID id, HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerAddress entity = customerAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        ensureOwnedByUser(entity.getCustomerUserId(), userId);
        customerAddressRepository.delete(entity);
        resyncAddressesJson(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/addresses/{id}/default")
    @Operation(summary = "Mark an address as default and clear other defaults")
    @Transactional
    public AddressResponse markAddressDefault(@PathVariable UUID id, HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerAddress target = customerAddressRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found: " + id));
        ensureOwnedByUser(target.getCustomerUserId(), userId);
        List<CustomerAddress> all = customerAddressRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        for (CustomerAddress a : all) {
            boolean shouldBeDefault = a.getId().equals(id);
            if (!java.util.Objects.equals(a.getIsDefault(), shouldBeDefault)) {
                a.setIsDefault(shouldBeDefault);
                customerAddressRepository.save(a);
            }
        }
        target.setIsDefault(true);
        resyncAddressesJson(userId);
        return toAddressResponse(target);
    }

    // ---------------------------------------------------------------------
    // Saved devices
    // ---------------------------------------------------------------------

    @GetMapping("/devices")
    @Operation(summary = "List own saved devices (optionally filter by categoryCode)")
    public List<SavedDeviceResponse> listDevices(
            @RequestParam(value = "categoryCode", required = false) String categoryCode,
            HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        var list = customerSavedDeviceRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        if (categoryCode != null && !categoryCode.isBlank()) {
            final String code = categoryCode.toUpperCase();
            list = list.stream()
                    .filter(d -> d.getCategoryCode() != null
                            && d.getCategoryCode().equalsIgnoreCase(code))
                    .toList();
        }
        return list.stream().map(this::toDeviceResponse).toList();
    }

    @PostMapping("/devices")
    @Operation(summary = "Create a new saved device")
    public SavedDeviceResponse createDevice(@Valid @RequestBody SavedDeviceRequest body,
                                            HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        // FK guard — see ensureLocalUser doc above.
        ensureLocalUser(userId);
        CustomerSavedDevice entity = CustomerSavedDevice.builder()
                .customerUserId(userId)
                .categoryId(body.getCategoryId())
                .categoryCode(body.getCategoryCode() != null ? body.getCategoryCode().toUpperCase() : null)
                .brandId(body.getBrandId())
                .modelId(body.getModelId())
                .modelName(body.getModelName())
                .brandName(body.getBrandName())
                .ramLabel(body.getRamLabel())
                .storageLabel(body.getStorageLabel())
                .ramOptionId(body.getRamOptionId())
                .storageOptionId(body.getStorageOptionId())
                .color(body.getColor())
                .imei(body.getImei())
                .note(body.getNote())
                .isDefault(Boolean.TRUE.equals(body.getIsDefault()))
                .build();
        CustomerSavedDevice saved = customerSavedDeviceRepository.save(entity);
        return toDeviceResponse(saved);
    }

    @PutMapping("/devices/{id}")
    @Operation(summary = "Update one of own saved devices")
    public SavedDeviceResponse updateDevice(@PathVariable UUID id,
                                            @Valid @RequestBody SavedDeviceRequest body,
                                            HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerSavedDevice entity = customerSavedDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Saved device not found: " + id));
        ensureOwnedByUser(entity.getCustomerUserId(), userId);
        if (body.getCategoryId() != null) entity.setCategoryId(body.getCategoryId());
        if (body.getCategoryCode() != null) entity.setCategoryCode(body.getCategoryCode().toUpperCase());
        if (body.getBrandId() != null) entity.setBrandId(body.getBrandId());
        if (body.getModelId() != null) entity.setModelId(body.getModelId());
        if (body.getModelName() != null) entity.setModelName(body.getModelName());
        if (body.getBrandName() != null) entity.setBrandName(body.getBrandName());
        if (body.getRamLabel() != null) entity.setRamLabel(body.getRamLabel());
        if (body.getStorageLabel() != null) entity.setStorageLabel(body.getStorageLabel());
        if (body.getRamOptionId() != null) entity.setRamOptionId(body.getRamOptionId());
        if (body.getStorageOptionId() != null) entity.setStorageOptionId(body.getStorageOptionId());
        if (body.getColor() != null) entity.setColor(body.getColor());
        if (body.getImei() != null) entity.setImei(body.getImei());
        if (body.getNote() != null) entity.setNote(body.getNote());
        if (body.getIsDefault() != null) entity.setIsDefault(body.getIsDefault());
        CustomerSavedDevice saved = customerSavedDeviceRepository.save(entity);
        return toDeviceResponse(saved);
    }

    @DeleteMapping("/devices/{id}")
    @Operation(summary = "Delete one of own saved devices")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id, HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerSavedDevice entity = customerSavedDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Saved device not found: " + id));
        ensureOwnedByUser(entity.getCustomerUserId(), userId);
        customerSavedDeviceRepository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices/{id}/default")
    @Operation(summary = "Mark a saved device as default and clear other defaults")
    @Transactional
    public SavedDeviceResponse markDeviceDefault(@PathVariable UUID id, HttpServletRequest request) {
        UUID userId = userIdFrom(request);
        CustomerSavedDevice target = customerSavedDeviceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Saved device not found: " + id));
        ensureOwnedByUser(target.getCustomerUserId(), userId);
        List<CustomerSavedDevice> all = customerSavedDeviceRepository.findByCustomerUserIdOrderByCreatedAtDesc(userId);
        for (CustomerSavedDevice d : all) {
            boolean shouldBeDefault = d.getId().equals(id);
            if (!java.util.Objects.equals(d.getIsDefault(), shouldBeDefault)) {
                d.setIsDefault(shouldBeDefault);
                customerSavedDeviceRepository.save(d);
            }
        }
        target.setIsDefault(true);
        return toDeviceResponse(target);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void ensureOwnedByUser(UUID ownerId, UUID userId) {
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new AccessDeniedException("Resource does not belong to current user");
        }
    }

    /**
     * Phase A dual-write: after any address mutation, rebuild the inline
     * customer_users.addresses jsonb mirror from the user's customer_addresses
     * rows. Element ids equal the row ids, so the "orders resolve addresses by
     * id" contract still holds once the source flips to jsonb in Phase B.
     */
    private void resyncAddressesJson(UUID userId) {
        List<AddressEntry> entries = customerAddressRepository
                .findByCustomerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAddressEntry).toList();
        customerUserRepository.findById(userId).ifPresent(u -> {
            u.setAddresses(entries);
            customerUserRepository.save(u);
        });
    }

    private AddressEntry toAddressEntry(CustomerAddress a) {
        return AddressEntry.builder()
                .id(a.getId())
                .label(a.getLabel())
                .fullName(a.getFullName())
                .mobile(a.getMobile())
                .pincode(a.getPincode())
                .locality(a.getLocality())
                .area(a.getArea() != null ? a.getArea() : a.getLocality())
                .addressLine(a.getAddressLine())
                .city(a.getCity())
                .district(a.getDistrict() != null ? a.getDistrict() : a.getCity())
                .taluk(a.getTaluk())
                .state(a.getState())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .isDefault(a.getIsDefault())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private CustomerProfileResponse toProfileResponse(CustomerUser u) {
        return CustomerProfileResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .mobile(u.getMobile())
                .alternateMobile(u.getAlternateMobile())
                .profileImageUrl(u.getProfileImageUrl())
                .isActive(u.getIsActive())
                .createdAt(u.getCreatedAt())
                .updatedAt(u.getUpdatedAt())
                .build();
    }

    private AddressResponse toAddressResponse(CustomerAddress a) {
        return AddressResponse.builder()
                .id(a.getId())
                .customerUserId(a.getCustomerUserId())
                .label(a.getLabel())
                .fullName(a.getFullName())
                .mobile(a.getMobile())
                .pincode(a.getPincode())
                .locality(a.getLocality())
                .area(a.getArea() != null ? a.getArea() : a.getLocality())
                .addressLine(a.getAddressLine())
                .city(a.getCity())
                .district(a.getDistrict() != null ? a.getDistrict() : a.getCity())
                .taluk(a.getTaluk())
                .state(a.getState())
                .latitude(a.getLatitude())
                .longitude(a.getLongitude())
                .isDefault(a.getIsDefault())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private SavedDeviceResponse toDeviceResponse(CustomerSavedDevice d) {
        return SavedDeviceResponse.builder()
                .id(d.getId())
                .customerUserId(d.getCustomerUserId())
                .categoryId(d.getCategoryId())
                .categoryCode(d.getCategoryCode())
                .brandId(d.getBrandId())
                .modelId(d.getModelId())
                .modelName(d.getModelName())
                .brandName(d.getBrandName())
                .ramLabel(d.getRamLabel())
                .storageLabel(d.getStorageLabel())
                .ramOptionId(d.getRamOptionId())
                .storageOptionId(d.getStorageOptionId())
                .color(d.getColor())
                .imei(d.getImei())
                .note(d.getNote())
                .isDefault(d.getIsDefault())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}
