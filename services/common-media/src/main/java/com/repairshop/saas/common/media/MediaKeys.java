package com.repairshop.saas.common.media;

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
 * Part-box photo    master/model-compatibility/{box-no}-{id}.{ext}
 *                    master/model-compatibility/a-12-9d3f7b10.jpg
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
    /**
     * Banners sit at the root rather than under master/, because they are customer-app
     * promotional content rather than catalogue taxonomy — and because that is the
     * path that was asked for: media.ggfix.in/banner/<title>.jpg
     */
    public static final String BANNER_ROOT = "banner";
    public static final String MASTER_BRANDS_ROOT = "master/brands";
    /** Reference photos of the spare part held in each compatibility box. */
    public static final String MASTER_COMPATIBILITY_ROOT = "master/model-compatibility";

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

    /** Home-screen banner: {@code banner/slider-1-8ab31f04.jpg}, named from its title. */
    public static String bannerImageKey(String title, String extension) {
        return BANNER_ROOT + "/" + uniqueName(Slugify.requireSlug(title, "banner title"), extension);
    }

    /** Brand artwork: {@code master/brands/vivo-4c7d1e02.png}. */
    public static String masterBrandImageKey(String brandName, String extension) {
        return MASTER_BRANDS_ROOT + "/"
                + uniqueName(Slugify.requireSlug(brandName, "brand"), extension);
    }

    /**
     * Reference photo for a spare-part box: {@code master/model-compatibility/a-12-9d3f7b10.jpg}.
     *
     * Keyed on the box NUMBER rather than its name, because the number is unique
     * (migration 79) and is how the shelf is labelled — the bucket then reads the
     * way the shelf does.
     */
    public static String modelCompatibilityImageKey(String boxNo, String extension) {
        return MASTER_COMPATIBILITY_ROOT + "/"
                + uniqueName(Slugify.requireSlug(boxNo, "box no"), extension);
    }

    // ------------------------------------------------------------------ helpers --

    private static String uniqueName(String stem, String extension) {
        return stem + "-" + shortId() + "." + extension.toLowerCase(Locale.ROOT);
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, UNIQUE_SUFFIX_LENGTH);
    }
}
