package com.repairshop.saas.auth.service;

import com.repairshop.saas.auth.entity.Shop;
import com.repairshop.saas.auth.entity.User;
import com.repairshop.saas.auth.repository.ShopRepository;
import com.repairshop.saas.auth.repository.UserRepository;
import com.repairshop.saas.common.media.MediaKeys;
import com.repairshop.saas.common.media.MediaProperties;
import com.repairshop.saas.common.media.MediaUploadValidator;
import com.repairshop.saas.common.media.MediaValidationException;
import com.repairshop.saas.common.media.S3StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner KYC and shop artwork on media.ggfix.in.
 *
 * <pre>
 * shopowner/ravi-kumar/aadhaar-front-3f9c11ab.jpg
 * shops/gg-mobiles-erode/shop-front-77b2e0d4.jpg
 * shops/gg-mobiles-erode/gst-2b81f0aa.pdf
 * </pre>
 *
 * <h2>Why this returns a URL rather than storing a key</h2>
 * master_models and the taxonomy tables store the object KEY and compose the URL at
 * read time, because this codebase owns both ends of those reads. The owner and shop
 * documents are different: {@code users.kyc_document} is a jsonb blob of URLs and
 * {@code shops.*_url} are URL columns, both already consumed by the shop app, the
 * employee app and the admin. Migrating them to keys would mean changing every one
 * of those readers at the same time. Writing the public URL into the existing column
 * gets the bytes out of the database and into S3 now, without a coordinated
 * client release — the same end state for storage, reached in one step instead of
 * several.
 *
 * Certificates accept PDF as well as images; artwork does not. A GST or Udyam
 * certificate is routinely issued as a PDF, whereas a shop banner that is secretly a
 * PDF just renders as a broken image.
 */
@Service
public class OwnerMediaService {

    private static final Logger log = LoggerFactory.getLogger(OwnerMediaService.class);

    /** Owner KYC slots. The value is the filename stem inside the owner's folder. */
    private static final Map<String, String> OWNER_DOCS = Map.of(
            "aadhaar-front", "aadhaar-front",
            "aadhaar-back", "aadhaar-back",
            "pan", "pan",
            "avatar", "avatar");

    /** Shop slots, split by whether a PDF is a legitimate upload for that slot. */
    private static final Set<String> SHOP_IMAGE_DOCS = Set.of("shop-front", "shop-banner");
    private static final Set<String> SHOP_CERTIFICATE_DOCS = Set.of("gst", "udyam");

    private final UserRepository userRepo;
    private final ShopRepository shopRepo;
    private final MediaUploadValidator validator;
    private final S3StorageService storage;
    private final MediaProperties props;

    public OwnerMediaService(UserRepository userRepo,
                             ShopRepository shopRepo,
                             MediaUploadValidator validator,
                             S3StorageService storage,
                             MediaProperties props) {
        this.userRepo = userRepo;
        this.shopRepo = shopRepo;
        this.validator = validator;
        this.storage = storage;
        this.props = props;
    }

    /**
     * Upload one owner document and return its public URL.
     *
     * The caller then saves that URL through the existing
     * {@code POST /auth/me/kyc-documents}, which already owns the review-status
     * transitions — duplicating that here would give two paths that can disagree
     * about when KYC goes back to PENDING_REVIEW.
     */
    public Map<String, String> uploadOwnerDocument(UUID userId, String documentType, MultipartFile file) {
        String slot = normalise(documentType);
        String stem = OWNER_DOCS.get(slot);
        if (stem == null) {
            throw new MediaValidationException(
                    "Unknown document type '" + documentType + "'. Expected one of " + OWNER_DOCS.keySet() + ".");
        }

        User owner = userRepo.findById(userId)
                .orElseThrow(() -> new MediaValidationException("No user with id " + userId + "."));
        if (owner.getName() == null || owner.getName().isBlank()) {
            throw new MediaValidationException(
                    "This account has no name set, so its media folder cannot be named. Set a full name first.");
        }

        // Avatars are artwork; identity documents are frequently scanned to PDF.
        MediaUploadValidator.ValidatedUpload upload = "avatar".equals(slot)
                ? validator.validateImage(file)
                : validator.validateDocument(file);

        String key = MediaKeys.documentKey(
                MediaKeys.shopOwnerFolder(owner.getName()), stem, upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());
        log.info("Owner {} uploaded {} -> {}", userId, slot, key);

        return response(key, url, upload);
    }

    /**
     * Upload one shop document (front, banner, GST, Udyam) and return its public URL.
     *
     * @param callerUserId the authenticated caller; must own the shop. Without this
     *        check any signed-in user could write into any shop's folder, and the
     *        folder name is public, so guessing one is trivial.
     */
    public Map<String, String> uploadShopDocument(UUID callerUserId, UUID shopId,
                                                  String documentType, MultipartFile file) {
        String slot = normalise(documentType);
        boolean isImage = SHOP_IMAGE_DOCS.contains(slot);
        boolean isCertificate = SHOP_CERTIFICATE_DOCS.contains(slot);
        if (!isImage && !isCertificate) {
            throw new MediaValidationException("Unknown document type '" + documentType
                    + "'. Expected one of " + SHOP_IMAGE_DOCS + " or " + SHOP_CERTIFICATE_DOCS + ".");
        }

        Shop shop = shopRepo.findById(shopId)
                .orElseThrow(() -> new MediaValidationException("No shop with id " + shopId + "."));
        // Mirrors the ownership guard in AuthService.setShopMobilePassword.
        if (shop.getOwnerUserId() == null || !shop.getOwnerUserId().equals(callerUserId)) {
            throw new MediaValidationException("That shop does not belong to this account.");
        }
        if (shop.getName() == null || shop.getName().isBlank()) {
            throw new MediaValidationException(
                    "This shop has no name set, so its media folder cannot be named.");
        }

        MediaUploadValidator.ValidatedUpload upload =
                isImage ? validator.validateImage(file) : validator.validateDocument(file);

        String key = MediaKeys.documentKey(MediaKeys.shopFolder(shop.getName()), slot, upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());
        log.info("Shop {} uploaded {} -> {}", shopId, slot, key);

        return response(key, url, upload);
    }

    private static Map<String, String> response(String key, String url,
                                                MediaUploadValidator.ValidatedUpload upload) {
        return Map.of(
                "key", key,
                "url", url,
                "contentType", upload.contentType(),
                "sizeBytes", String.valueOf(upload.size()),
                "originalName", upload.originalName() == null ? "" : upload.originalName());
    }

    /** Accepts "Aadhar Card Back", "aadhaar_back" and "aadhaar-back" alike. */
    private static String normalise(String documentType) {
        if (documentType == null) {
            return "";
        }
        return documentType.trim().toLowerCase(Locale.ROOT)
                .replace("aadhar", "aadhaar")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "")
                .replace("aadhaar-card-", "aadhaar-");
    }

    /** Exposed so callers can show where a file will land before uploading. */
    public String publicUrl(String key) {
        return props.publicUrl(key);
    }
}
