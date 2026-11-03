package com.repairshop.saas.marketplace.service;

import com.repairshop.saas.marketplace.entity.MarketplaceProduct;
import com.repairshop.saas.marketplace.exception.ConflictException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Listing statuses, transitions and the "publishable" rule (SELL-MODULE-API.md). */
public final class SellRules {

    private SellRules() {}

    public static final String DRAFT = "DRAFT";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";
    public static final String SOLD = "SOLD";
    public static final String DELETED = "DELETED";

    public static final Set<String> LISTING_TYPES = Set.of("DEVICE", "SPARE_PART", "ACCESSORY");
    public static final Set<String> ITEM_CONDITIONS = Set.of("NEW", "USED", "REFURBISHED");
    public static final Set<String> FULFILMENT_OPTIONS = Set.of("PICKUP", "SHIPPING");
    public static final Set<String> LIST_FILTERS = Set.of("ALL", ACTIVE, DRAFT, SOLD, INACTIVE);
    public static final Set<String> PATCHABLE = Set.of(ACTIVE, INACTIVE, DRAFT, SOLD);

    public static final int MAX_IMAGES = 10;
    public static final int MIN_DEVICE_IMAGES = 3;
    public static final int MAX_CLIENT_REF = 64;

    /** Raw statuses of a live listing (not sold, not deleted): the IMEI-uniqueness scope. */
    public static final List<String> LIVE_STATUSES = List.of(DRAFT, ACTIVE, INACTIVE, "CANCELLED", "CANCELED");

    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            DRAFT, Set.of(ACTIVE),
            ACTIVE, Set.of(INACTIVE, DRAFT, SOLD),
            INACTIVE, Set.of(ACTIVE, DRAFT, SOLD));

    /** Legacy COMPLETED counts as SOLD, legacy CANCELLED as INACTIVE. */
    public static String normalizeStatus(String raw) {
        if (raw == null || raw.isBlank()) return ACTIVE;
        String s = raw.trim().toUpperCase();
        if ("COMPLETED".equals(s)) return SOLD;
        if ("CANCELLED".equals(s) || "CANCELED".equals(s)) return INACTIVE;
        return s;
    }

    /** Raw DB statuses behind a normalised status bucket. */
    public static List<String> rawStatuses(String bucket) {
        return switch (bucket) {
            case SOLD -> List.of(SOLD, "COMPLETED");
            case INACTIVE -> List.of(INACTIVE, "CANCELLED", "CANCELED");
            default -> List.of(bucket);
        };
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(normalizeStatus(from), Set.of()).contains(to);
    }

    /** 409 unless {@code from -> to} is one of the PATCH transitions. */
    public static void requireTransition(String from, String to) {
        String cur = normalizeStatus(from);
        if (!canTransition(cur, to)) {
            throw new ConflictException("This " + cur.toLowerCase() + " listing can't be marked " + to.toLowerCase() + ".");
        }
    }

    /**
     * ACTIVE/INACTIVE -> SOLD. A {@code quantitySold} below the remaining stock
     * sells part of it and keeps the status; otherwise the listing sells out.
     *
     * @return true when the listing is now SOLD (stock 0)
     */
    public static boolean applySale(MarketplaceProduct p, Integer quantitySold) {
        int qty = p.getQuantity() == null ? 0 : p.getQuantity();
        int sold = p.getSoldQuantity() == null ? 0 : p.getSoldQuantity();
        if (quantitySold != null && quantitySold < qty) {
            p.setQuantity(qty - quantitySold);
            p.setSoldQuantity(sold + quantitySold);
            return false;
        }
        p.setSoldQuantity(sold + qty);
        p.setQuantity(0);
        p.setStatus(SOLD);
        return true;
    }

    public static int imageCount(MarketplaceProduct p) {
        int main = (p.getImageUrl() != null && !p.getImageUrl().isBlank()) ? 1 : 0;
        return main + ProductMapper.parseExtraImages(p.getExtraImageUrls()).size();
    }

    /** Empty when the listing may be ACTIVE; otherwise the reasons it can't. */
    public static List<String> publishProblems(MarketplaceProduct p) {
        List<String> problems = new ArrayList<>();
        if (p.getTitle() == null || p.getTitle().isBlank()) problems.add("title is required");
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) problems.add("price must be greater than 0");
        if (p.getQuantity() == null || p.getQuantity() < 1) problems.add("quantity must be at least 1");
        int images = imageCount(p);
        if ("DEVICE".equals(p.getListingType()) && (images < MIN_DEVICE_IMAGES || images > MAX_IMAGES)) {
            problems.add("a device listing needs " + MIN_DEVICE_IMAGES + " to " + MAX_IMAGES + " photos (has " + images + ")");
        } else if (images > MAX_IMAGES) {
            problems.add("at most " + MAX_IMAGES + " photos are allowed");
        }
        return problems;
    }
}
