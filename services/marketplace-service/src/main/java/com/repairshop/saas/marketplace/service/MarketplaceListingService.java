package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.dto.CreateListingRequest;
import com.repairshop.saas.marketplace.dto.ListingView;
import com.repairshop.saas.marketplace.entity.MarketplaceListing;
import com.repairshop.saas.marketplace.repository.MarketplaceListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceListingService {

    private static final double EARTH_KM = 6371.0;

    private final MarketplaceListingRepository repo;

    @Transactional
    public void delete(UUID id) {
        repo.deleteById(id);
    }

    @Transactional
    public ListingView create(CreateListingRequest req) {
        MarketplaceListing l = MarketplaceListing.builder()
                .sellerType(req.getSellerType())
                .sellerId(req.getSellerId())
                .shopId(req.getShopId())
                .categoryId(req.getCategoryId())
                .brandId(req.getBrandId())
                .modelId(req.getModelId())
                .productName(req.getProductName())
                .productImage(req.getProductImage())
                .condition(req.getCondition())
                .description(req.getDescription())
                .expectedPrice(req.getExpectedPrice())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .address(req.getAddress())
                .city(req.getCity())
                .state(req.getState())
                .pincode(req.getPincode())
                .status("AVAILABLE")
                .build();
        return toView(repo.save(l), null);
    }

    /**
     * Buy-screen feed: AVAILABLE listings within {@code radiusKm} of (lat,lng),
     * sorted by distance ascending. If lat/lng are null we fall back to a
     * recency-sorted unfiltered listing (so the screen shows something).
     * Listings without lat/lng are excluded from distance results.
     */
    @Transactional(readOnly = true)
    public List<ListingView> findNearby(BigDecimal lat, BigDecimal lng, double radiusKm, String q, UUID excludeSellerId) {
        List<MarketplaceListing> rows = repo.findByStatusAndQuery("AVAILABLE", q);
        final boolean hasOrigin = (lat != null && lng != null);
        final double oLat;
        final double oLng;
        if (hasOrigin) {
            oLat = lat.doubleValue();
            oLng = lng.doubleValue();
        } else {
            oLat = 0.0;
            oLng = 0.0;
        }

        return rows.stream()
                .filter(l -> excludeSellerId == null || !excludeSellerId.equals(l.getSellerId()))
                .map(l -> {
                    Double d = null;
                    if (hasOrigin && l.getLatitude() != null && l.getLongitude() != null) {
                        d = haversineKm(oLat, oLng, l.getLatitude().doubleValue(), l.getLongitude().doubleValue());
                    }
                    return toView(l, d);
                })
                .filter(v -> !hasOrigin || v.getDistanceKm() == null || v.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(
                        v -> v.getDistanceKm() == null ? Double.MAX_VALUE : v.getDistanceKm()))
                .toList();
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_KM * c;
    }

    private static ListingView toView(MarketplaceListing l, Double distanceKm) {
        return ListingView.builder()
                .id(l.getId())
                .sellerType(l.getSellerType())
                .sellerId(l.getSellerId())
                .shopId(l.getShopId())
                .brandId(l.getBrandId())
                .modelId(l.getModelId())
                .categoryId(l.getCategoryId())
                .productName(l.getProductName())
                .productImage(l.getProductImage())
                .condition(l.getCondition())
                .description(l.getDescription())
                .expectedPrice(l.getExpectedPrice())
                .latitude(l.getLatitude())
                .longitude(l.getLongitude())
                .address(l.getAddress())
                .city(l.getCity())
                .state(l.getState())
                .pincode(l.getPincode())
                .status(l.getStatus())
                .createdAt(l.getCreatedAt())
                .distanceKm(distanceKm == null ? null : Math.round(distanceKm * 10) / 10.0)
                .build();
    }
}
