package com.repairshop.saas.shop.controller;

import com.repairshop.saas.shop.dto.*;
import com.repairshop.saas.shop.entity.Shop;
import com.repairshop.saas.shop.entity.ShopImage;
import com.repairshop.saas.shop.entity.ShopOfferedService;
import com.repairshop.saas.shop.entity.ShopPickupSlot;
import com.repairshop.saas.shop.exception.ResourceNotFoundException;
import com.repairshop.saas.shop.repository.ShopImageRepository;
import com.repairshop.saas.shop.repository.ShopOfferedServiceRepository;
import com.repairshop.saas.shop.repository.ShopPickupSlotRepository;
import com.repairshop.saas.shop.repository.ShopRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/shops")
@RequiredArgsConstructor
public class ShopDirectoryController {

    private final ShopRepository shopRepository;
    private final ShopOfferedServiceRepository offeredServiceRepository;
    private final ShopImageRepository shopImageRepository;
    private final ShopPickupSlotRepository pickupSlotRepository;

    // ---- Authorization helpers -------------------------------------------------
    // Shop mutations were previously gated by "authenticated" only, letting any
    // valid token (a customer, an employee, or a DIFFERENT shop's owner) rewrite
    // or deactivate any shop. These guards enforce owner/admin role + shop-scope.
    @SuppressWarnings("unchecked")
    private List<String> rolesFrom(HttpServletRequest request) {
        Object r = request.getAttribute("roles");
        return (r instanceof List) ? (List<String>) r : List.of();
    }

    private UUID tokenShopId(HttpServletRequest request) {
        String sid = (String) request.getAttribute("shopId");
        return sid != null ? UUID.fromString(sid) : null;
    }

    // Owner may manage only their OWN shop; SUPER_ADMIN may manage any shop.
    // NOTE (multi-shop owners): this compares the path shop id to the JWT's
    // shopId claim, so an owner editing a shop other than the one their current
    // token was minted for will be rejected — verify the shop-switch flow
    // re-mints the token's shopId before relying on this in production.
    private void requireShopOwner(HttpServletRequest request, UUID shopId) {
        List<String> roles = rolesFrom(request);
        if (roles.contains("SUPER_ADMIN")) return;
        if (roles.contains("SHOP_OWNER") && shopId != null && shopId.equals(tokenShopId(request))) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own shop.");
    }

    // Owner/admin role required (used where there is no path shop id yet).
    private void requireStaff(HttpServletRequest request) {
        List<String> roles = rolesFrom(request);
        if (roles.contains("SUPER_ADMIN") || roles.contains("SHOP_OWNER")) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Owner or admin role required.");
    }

    // -------------------------------------------------------------------------
    // PUBLIC READ ENDPOINTS
    // -------------------------------------------------------------------------

    @GetMapping
    public ResponseEntity<List<ShopSummaryResponse>> listShops(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit) {

        String needle = q == null ? null : q.trim().toLowerCase();
        List<ShopSummaryResponse> result = shopRepository.findByIsActiveTrue().stream()
                .filter(s -> {
                    if (needle == null || needle.isEmpty()) return true;
                    String name = s.getName() == null ? "" : s.getName().toLowerCase();
                    String city = s.getCity() == null ? "" : s.getCity().toLowerCase();
                    return name.contains(needle) || city.contains(needle);
                })
                .sorted(Comparator.comparing(
                        (Shop s) -> s.getRating() == null ? BigDecimal.ZERO : s.getRating()).reversed())
                .limit(limit == null || limit <= 0 ? 50 : limit)
                .map(s -> toSummary(s, null))
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<ShopSummaryResponse>> nearby(
            @RequestParam(value = "lat", required = false) Double lat,
            @RequestParam(value = "lng", required = false) Double lng,
            @RequestParam(value = "radiusKm", required = false) Double radiusKm,
            @RequestParam(value = "limit", required = false, defaultValue = "50") Integer limit,
            @RequestParam(value = "activeOnly", required = false, defaultValue = "false") Boolean activeOnly) {

        // Default: return ALL shops so the customer view never silently goes
        // empty just because an admin forgot to toggle Active. Pass
        // ?activeOnly=true to opt in to the stricter filter.
        List<Shop> shops = Boolean.TRUE.equals(activeOnly)
                ? shopRepository.findByIsActiveTrue()
                : shopRepository.findAll();

        if (lat == null || lng == null) {
            List<ShopSummaryResponse> sorted = shops.stream()
                    .sorted(Comparator.comparing(
                            (Shop s) -> s.getRating() == null ? BigDecimal.ZERO : s.getRating()).reversed())
                    .limit(limit == null || limit <= 0 ? 50 : limit)
                    .map(s -> toSummary(s, null))
                    .toList();
            return ResponseEntity.ok(sorted);
        }

        final double effectiveRadius = radiusKm == null ? Double.MAX_VALUE : radiusKm;
        List<ShopSummaryResponse> result = shops.stream()
                .map(s -> {
                    Double distance = null;
                    if (s.getLatitude() != null && s.getLongitude() != null) {
                        distance = haversineKm(
                                lat, lng,
                                s.getLatitude().doubleValue(),
                                s.getLongitude().doubleValue());
                    }
                    return toSummary(s, distance);
                })
                .filter(s -> {
                    if (s.getDistanceKm() == null) return false;
                    return s.getDistanceKm() <= effectiveRadius;
                })
                .sorted(Comparator.comparing(
                        ShopSummaryResponse::getDistanceKm,
                        Comparator.nullsLast(Double::compareTo)))
                .limit(limit == null || limit <= 0 ? 50 : limit)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShopDetailsResponse> getShop(@PathVariable UUID id) {
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + id));
        return ResponseEntity.ok(toDetails(shop));
    }

    @GetMapping("/by-slug/{slug}")
    public ResponseEntity<ShopDetailsResponse> getShopBySlug(@PathVariable String slug) {
        Shop shop = shopRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + slug));
        return ResponseEntity.ok(toDetails(shop));
    }

    @GetMapping("/{id}/pickup-slots")
    public ResponseEntity<List<PickupSlotResponse>> getPickupSlots(@PathVariable UUID id) {
        if (!shopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shop not found: " + id);
        }
        List<PickupSlotResponse> slots = pickupSlotRepository.findByShopId(id).stream()
                .map(this::toSlotResponse)
                .toList();
        return ResponseEntity.ok(slots);
    }

    // -------------------------------------------------------------------------
    // ADMIN ENDPOINTS (require auth via SecurityConfig)
    // -------------------------------------------------------------------------

    @PostMapping
    public ResponseEntity<ShopDetailsResponse> createShop(@RequestBody ShopUpsertRequest req,
                                                          HttpServletRequest request) {
        requireStaff(request);
        Shop shop = Shop.builder()
                .name(req.getName())
                .slug(req.getSlug())
                .email(req.getEmail())
                .address(req.getAddress())
                .timezone(req.getTimezone())
                .isActive(req.getIsActive() == null ? Boolean.TRUE : req.getIsActive())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .city(req.getCity())
                .state(req.getState())
                .pincode(req.getPincode())
                .rating(req.getRating())
                .build();
        Shop saved = shopRepository.save(shop);
        return ResponseEntity.ok(toDetails(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShopDetailsResponse> updateShop(@PathVariable UUID id,
                                                          @RequestBody ShopUpsertRequest req,
                                                          HttpServletRequest request) {
        requireShopOwner(request, id);
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + id));
        if (req.getName() != null) shop.setName(req.getName());
        if (req.getSlug() != null) shop.setSlug(req.getSlug());
        if (req.getEmail() != null) shop.setEmail(req.getEmail());
        if (req.getAddress() != null) shop.setAddress(req.getAddress());
        if (req.getTimezone() != null) shop.setTimezone(req.getTimezone());
        if (req.getIsActive() != null) shop.setIsActive(req.getIsActive());
        if (req.getLatitude() != null) shop.setLatitude(req.getLatitude());
        if (req.getLongitude() != null) shop.setLongitude(req.getLongitude());
        if (req.getCity() != null) shop.setCity(req.getCity());
        if (req.getState() != null) shop.setState(req.getState());
        if (req.getPincode() != null) shop.setPincode(req.getPincode());
        if (req.getRating() != null) shop.setRating(req.getRating());
        return ResponseEntity.ok(toDetails(shopRepository.save(shop)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ShopDetailsResponse> setStatus(@PathVariable UUID id,
                                                        @RequestParam("active") boolean active,
                                                        HttpServletRequest request) {
        requireShopOwner(request, id);
        Shop shop = shopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found: " + id));
        shop.setIsActive(active);
        return ResponseEntity.ok(toDetails(shopRepository.save(shop)));
    }

    @PostMapping("/{id}/services")
    public ResponseEntity<ShopOfferedService> addService(@PathVariable UUID id,
                                                         @RequestBody ShopServiceRequest req,
                                                         HttpServletRequest request) {
        requireShopOwner(request, id);
        if (!shopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shop not found: " + id);
        }
        if (req.getServiceCode() == null || req.getServiceCode().isBlank()) {
            throw new IllegalArgumentException("serviceCode is required");
        }
        ShopOfferedService entity = offeredServiceRepository
                .findByShopIdAndServiceCode(id, req.getServiceCode())
                .orElseGet(() -> ShopOfferedService.builder()
                        .shopId(id)
                        .serviceCode(req.getServiceCode())
                        .isEnabled(req.getIsEnabled() == null ? Boolean.TRUE : req.getIsEnabled())
                        .build());
        if (req.getIsEnabled() != null) entity.setIsEnabled(req.getIsEnabled());
        return ResponseEntity.ok(offeredServiceRepository.save(entity));
    }

    @DeleteMapping("/{id}/services/{code}")
    public ResponseEntity<Void> removeService(@PathVariable UUID id, @PathVariable String code,
                                              HttpServletRequest request) {
        requireShopOwner(request, id);
        ShopOfferedService entity = offeredServiceRepository.findByShopIdAndServiceCode(id, code)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found for shop: " + code));
        offeredServiceRepository.delete(entity);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<ShopImage> addImage(@PathVariable UUID id,
                                              @RequestBody ShopImageRequest req,
                                              HttpServletRequest request) {
        requireShopOwner(request, id);
        if (!shopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shop not found: " + id);
        }
        if (req.getImageUrl() == null || req.getImageUrl().isBlank()) {
            throw new IllegalArgumentException("imageUrl is required");
        }
        ShopImage image = ShopImage.builder()
                .shopId(id)
                .imageUrl(req.getImageUrl())
                .sortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder())
                .build();
        return ResponseEntity.ok(shopImageRepository.save(image));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    public ResponseEntity<Void> removeImage(@PathVariable UUID id, @PathVariable UUID imageId,
                                            HttpServletRequest request) {
        requireShopOwner(request, id);
        ShopImage image = shopImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found: " + imageId));
        if (!image.getShopId().equals(id)) {
            throw new ResourceNotFoundException("Image not found for shop: " + imageId);
        }
        shopImageRepository.delete(image);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pickup-slots")
    public ResponseEntity<PickupSlotResponse> addPickupSlot(@PathVariable UUID id,
                                                            @RequestBody ShopPickupSlotRequest req,
                                                            HttpServletRequest request) {
        requireShopOwner(request, id);
        if (!shopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shop not found: " + id);
        }
        validateSlotRequest(req);
        checkOverlap(id, req, null);
        ShopPickupSlot slot = ShopPickupSlot.builder()
                .shopId(id)
                .dayOfWeek(req.getDayOfWeek())
                .startTime(req.getStartTime())
                .endTime(req.getEndTime())
                .capacity(req.getCapacity() == null ? 10 : req.getCapacity())
                .build();
        return ResponseEntity.ok(toSlotResponse(pickupSlotRepository.save(slot)));
    }

    @PutMapping("/{id}/pickup-slots/{slotId}")
    public ResponseEntity<PickupSlotResponse> updatePickupSlot(@PathVariable UUID id,
                                                               @PathVariable UUID slotId,
                                                               @RequestBody ShopPickupSlotRequest req,
                                                               HttpServletRequest request) {
        requireShopOwner(request, id);
        if (!shopRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shop not found: " + id);
        }
        ShopPickupSlot slot = pickupSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));
        if (!slot.getShopId().equals(id)) {
            throw new ResourceNotFoundException("Slot not found for shop: " + slotId);
        }
        validateSlotRequest(req);
        checkOverlap(id, req, slotId);
        slot.setDayOfWeek(req.getDayOfWeek());
        slot.setStartTime(req.getStartTime());
        slot.setEndTime(req.getEndTime());
        slot.setCapacity(req.getCapacity() == null ? 10 : req.getCapacity());
        return ResponseEntity.ok(toSlotResponse(pickupSlotRepository.save(slot)));
    }

    @DeleteMapping("/{id}/pickup-slots/{slotId}")
    public ResponseEntity<Void> removePickupSlot(@PathVariable UUID id, @PathVariable UUID slotId,
                                                 HttpServletRequest request) {
        requireShopOwner(request, id);
        ShopPickupSlot slot = pickupSlotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found: " + slotId));
        if (!slot.getShopId().equals(id)) {
            throw new ResourceNotFoundException("Slot not found for shop: " + slotId);
        }
        pickupSlotRepository.delete(slot);
        return ResponseEntity.noContent().build();
    }

    private void validateSlotRequest(ShopPickupSlotRequest req) {
        if (req.getStartTime() == null || req.getEndTime() == null) {
            throw new IllegalArgumentException("startTime and endTime are required");
        }
        if (!req.getStartTime().isBefore(req.getEndTime())) {
            throw new IllegalArgumentException("startTime must be before endTime");
        }
        // dayOfWeek null = any-day per ShopPickupSlotRequest doc; otherwise 1..7 ISO.
        if (req.getDayOfWeek() != null && (req.getDayOfWeek() < 1 || req.getDayOfWeek() > 7)) {
            throw new IllegalArgumentException("dayOfWeek must be 1..7 (Mon..Sun) or null for any-day");
        }
        if (req.getCapacity() != null && req.getCapacity() < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
    }

    private void checkOverlap(UUID shopId, ShopPickupSlotRequest req, UUID excludeSlotId) {
        Short day = req.getDayOfWeek();
        List<ShopPickupSlot> existing = pickupSlotRepository.findByShopId(shopId);
        for (ShopPickupSlot s : existing) {
            if (excludeSlotId != null && s.getId().equals(excludeSlotId)) continue;
            // any-day slots (null dayOfWeek) overlap with everything; same-day slots only overlap each other.
            boolean sameDay = day == null || s.getDayOfWeek() == null
                    || java.util.Objects.equals(s.getDayOfWeek(), day);
            if (!sameDay) continue;
            if (s.getStartTime().isBefore(req.getEndTime()) && req.getStartTime().isBefore(s.getEndTime())) {
                throw new IllegalArgumentException(
                        "Overlaps existing slot " + s.getStartTime() + "–" + s.getEndTime()
                        + (s.getDayOfWeek() == null ? " (any day)" : " (day " + s.getDayOfWeek() + ")"));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private ShopSummaryResponse toSummary(Shop s, Double distanceKm) {
        return ShopSummaryResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .slug(s.getSlug())
                .city(s.getCity())
                .address(s.getAddress())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .rating(s.getRating())
                .distanceKm(distanceKm)
                .isOpen(Boolean.TRUE)
                .build();
    }

    private ShopDetailsResponse toDetails(Shop s) {
        List<String> services = offeredServiceRepository.findByShopId(s.getId()).stream()
                .filter(svc -> svc.getIsEnabled() == null || svc.getIsEnabled())
                .map(ShopOfferedService::getServiceCode)
                .toList();
        List<String> images = shopImageRepository.findByShopIdOrderBySortOrderAsc(s.getId()).stream()
                .map(ShopImage::getImageUrl)
                .toList();
        List<PickupSlotResponse> slots = pickupSlotRepository.findByShopId(s.getId()).stream()
                .map(this::toSlotResponse)
                .toList();
        return ShopDetailsResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .slug(s.getSlug())
                .city(s.getCity())
                .address(s.getAddress())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .rating(s.getRating())
                .isOpen(Boolean.TRUE)
                .email(s.getEmail())
                .state(s.getState())
                .pincode(s.getPincode())
                .services(services)
                .images(images)
                .pickupSlots(slots)
                .build();
    }

    private PickupSlotResponse toSlotResponse(ShopPickupSlot slot) {
        return PickupSlotResponse.builder()
                .id(slot.getId())
                .dayOfWeek(slot.getDayOfWeek())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .capacity(slot.getCapacity())
                .build();
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0088; // mean Earth radius in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
