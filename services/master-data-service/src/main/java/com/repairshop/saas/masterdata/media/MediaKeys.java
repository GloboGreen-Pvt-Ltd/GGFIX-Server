package com.repairshop.saas.masterdata.media;

import java.util.Locale;
import java.util.UUID;

/**
 * Builds every S3 object key used under media.ggfix.in.
 *
 * Pure functions, deliberately: keys are derived only from their arguments, with no
 * database or config access, so the same inputs always produce the same folder. The
 * public URL is composed elsewhere ({@link MediaProperties#publicUrl}) — a key must
 * never carry the CDN hostname, or moving domains becomes a data migration.
 *
 * <h2>Layouts</h2>
 * <pre>
 * Device catalogue   {category}/{brand}/{series}/{model}/main-{id}.{ext}
 *                    mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg
 *
 * Shop owner KYC     shopowner/{owner}/{doc}-{id}.{ext}
 *                    shopowner/ravi-kumar/aadhaar-front-3f9c11ab.jpg
 *
 * Shop artwork       shops/{shop}/{doc}-{id}.{ext}
 *                    shops/gg-mobiles-erode/shop-front-77b2e0d4.jpg
 *
 * Master taxonomy    master/categories/{slug}-{id}.{ext}
 *                    master/brands/{slug}-{id}.{ext}
 *
 * Device files       Devicefiles/{slot}-{id}.{ext}
 *                    Devicefiles/front-3f9c11ab.jpg
 *                    Devicefiles/video-77b2e0d4.mp4
 * </pre>
 *
 * The catalogue layout is the reason this is folder-shaped rather than a flat
 * hash: every model under one series shares the first three segments, so the
 * bucket browses like the catalogue and a whole series can be listed, copied or
 * lifecycled by prefix.
 */
public final class MediaKeys {

    /** Top-level prefixes. Changing one orphans existing objects — treat as fixed. */
    public static final String SHOP_OWNER_ROOT = "shopowner";
    public static final String SHOP_ROOT = "shops";
    public static final String MASTER_CATEGORIES_ROOT = "master/categories";
    public static final String MASTER_BRANDS_ROOT = "master/brands";

    /**
     * Photos and coverage videos of a customer's device, captured on the "Device
     * Information → Device Files" step of every booking flow (shop counter, customer
     * pickup, employee pickup estimate). Deliberately spelled {@code Devicefiles} —
     * the live URLs are already this shape and S3 keys are case-sensitive.
     */
    public static final String DEVICE_FILES_ROOT = "Devicefiles";

    /**
     * Hex characters of randomness in each filename. Uniqueness is what stops
     * CloudFront and the browser serving a stale image after a replacement, so the
     * leaf MUST change on every upload even when the folder does not. 8 hex chars
     * inside a per-model folder makes a collision not worth engineering against.
     */
    private static final int UNIQUE_SUFFIX_LENGTH = 8;

    private MediaKeys() {
    }

    // ---------------------------------------------------------------- catalogue --

    /**
     * Folder shared by every image of one model, e.g. {@code mobile/vivo/y-series/vivo-y20}.
     * Stable across image replacements — only the leaf filename changes.
     *
     * @throws MediaValidationException if any part slugifies to nothing
     */
    public static String modelFolder(String categoryName, String brandName, String seriesName, String modelName) {
        return String.join("/",
                Slugify.requireSlug(categoryName, "category"),
                Slugify.requireSlug(brandName, "brand"),
                Slugify.requireSlug(seriesName, "series"),
                Slugify.requireSlug(modelName, "model name"));
    }

    /** Full object key for a model's primary image: {@code {folder}/main-{id}.{ext}}. */
    public static String modelImageKey(String modelFolder, String extension) {
        return modelFolder + "/" + uniqueName("main", extension);
    }

    // ------------------------------------------------------------ owner and shop --

    /** @param ownerName the owner's full name, e.g. "Ravi Kumar" -> {@code shopowner/ravi-kumar} */
    public static String shopOwnerFolder(String ownerName) {
        return SHOP_OWNER_ROOT + "/" + Slugify.requireSlug(ownerName, "owner name");
    }

    /** @param shopName the shop's name -> {@code shops/gg-mobiles-erode} */
    public static String shopFolder(String shopName) {
        return SHOP_ROOT + "/" + Slugify.requireSlug(shopName, "shop name");
    }

    /**
     * A named document inside an owner or shop folder — {@code avatar},
     * {@code aadhaar-front}, {@code aadhaar-back}, {@code pan}, {@code shop-front},
     * {@code shop-banner}, {@code gst}, {@code udyam}.
     *
     * The document name is slugified too: these come from enums today, but a
     * hand-passed "Aadhar Card Back" must not put a space in a key.
     */
    public static String documentKey(String folder, String documentName, String extension) {
        return folder + "/" + uniqueName(Slugify.requireSlug(documentName, "document name"), extension);
    }

    // ------------------------------------------------------------------- master --

    /** Category artwork: {@code master/categories/mobile-1f0ab993.png}. */
    public static String masterCategoryImageKey(String categoryName, String extension) {
        return MASTER_CATEGORIES_ROOT + "/"
                + uniqueName(Slugify.requireSlug(categoryName, "category"), extension);
    }

    /** Brand artwork: {@code master/brands/vivo-4c7d1e02.png}. */
    public static String masterBrandImageKey(String brandName, String extension) {
        return MASTER_BRANDS_ROOT + "/"
                + uniqueName(Slugify.requireSlug(brandName, "brand"), extension);
    }

    // ---------------------------------------------------------- generic uploads --

    /**
     * Longest folder path we will build a key from. The app folders are two or three
     * segments ({@code tickets/{id}/solution-packs/audio} is the deepest); anything
     * beyond this is a caller bug or an attack, and truncating is friendlier than
     * failing an upload the user cannot diagnose.
     */
    private static final int MAX_FOLDER_DEPTH = 6;

    /**
     * Key for an upload posted to {@code /media/upload} with a free-form folder.
     *
     * Every app already sends a {@code folder} — {@code sell}, {@code chat},
     * {@code owner-kyc}, {@code tickets/{id}/notes} — so the bucket inherits a layout
     * the apps already agree on rather than needing a new taxonomy. Device-file
     * folders are the one exception: they all collapse onto {@link #DEVICE_FILES_ROOT}
     * so the three booking flows (shop counter, customer pickup, employee pickup)
     * write to one place.
     *
     * Each segment is slugified, which is also the containment: a folder of
     * {@code ../../etc} becomes {@code etc}, so no caller can climb out of the bucket
     * or forge a key under {@code master/}.
     *
     * @param folder caller's folder, possibly nested, possibly null
     * @param slot   optional filename stem — {@code front}, {@code back},
     *               {@code video}. Falls back to the folder's last segment, so
     *               {@code sell} yields {@code sell/sell-3f9c11ab.jpg}.
     */
    public static String uploadKey(String folder, String slot, String extension) {
        String prefix = isDeviceFileFolder(folder) ? DEVICE_FILES_ROOT : sanitizeFolder(folder);
        String stem = Slugify.slugify(slot);
        if (stem == null) {
            stem = lastSegment(prefix);
        }
        return prefix + "/" + uniqueName(stem, extension);
    }

    /**
     * True when this folder holds device photos/videos from a booking flow. Matched on
     * the FIRST segment only, because the employee app scopes its folder per booking
     * ({@code pickup-estimates/{bookingId}}).
     *
     * <ul>
     *   <li>{@code repair} — shop app, counter booking</li>
     *   <li>{@code repair-bookings} — customer app, pickup booking</li>
     *   <li>{@code pickup-estimates} — employee app, pickup device info + estimate</li>
     *   <li>{@code device-files} — explicit opt-in for anything added later</li>
     * </ul>
     */
    public static boolean isDeviceFileFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return false;
        }
        String first = Slugify.slugify(folder.trim().split("[/\\\\]", 2)[0]);
        return first != null && DEVICE_FILE_FOLDERS.contains(first);
    }

    private static final java.util.Set<String> DEVICE_FILE_FOLDERS =
            java.util.Set.of("repair", "repair-bookings", "pickup-estimates", "device-files");

    /**
     * @return the folder as slugified path segments, or {@code uploads} when the
     *         caller sent nothing usable. Unlike the catalogue helpers this never
     *         throws: a missing folder is a shrug, not a reason to fail an upload the
     *         user is waiting on.
     */
    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return "uploads";
        }
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (String part : folder.split("[/\\\\]+")) {
            String slug = Slugify.slugify(part);
            if (slug == null) {
                continue;
            }
            if (depth > 0) {
                out.append('/');
            }
            out.append(slug);
            if (++depth == MAX_FOLDER_DEPTH) {
                break;
            }
        }
        return out.isEmpty() ? "uploads" : out.toString();
    }

    private static String lastSegment(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    // ------------------------------------------------------------------ helpers --

    private static String uniqueName(String stem, String extension) {
        return stem + "-" + shortId() + "." + extension.toLowerCase(Locale.ROOT);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, UNIQUE_SUFFIX_LENGTH);
    }
}
