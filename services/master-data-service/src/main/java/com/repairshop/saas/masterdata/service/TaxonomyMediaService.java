package com.repairshop.saas.masterdata.service;

import com.repairshop.saas.common.media.MediaKeys;
import com.repairshop.saas.common.media.MediaUploadValidator;
import com.repairshop.saas.common.media.MediaValidationException;
import com.repairshop.saas.common.media.S3StorageService;
import com.repairshop.saas.masterdata.dto.TaxonomyImageResponse;
import com.repairshop.saas.masterdata.entity.MasterBanner;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.entity.ModelCompatibility;
import com.repairshop.saas.masterdata.repository.MasterBannerRepository;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceCategoryRepository;
import com.repairshop.saas.masterdata.repository.ModelCompatibilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Category and brand artwork on media.ggfix.in.
 *
 * <h2>Why this stores a URL and adds no columns</h2>
 * The first version of this added image_key / image_original_name / image_content_type
 * / image_size_bytes to both tables. That required a migration, the migration was not
 * applied to the production database, and the resulting drift 500ed the whole
 * catalogue — twice.
 *
 * None of that metadata was load-bearing. What the product needs is the bytes in S3
 * instead of inlined in Postgres, and image_url already holds a URL that every client
 * — admin, customer app, shop app — reads today. So the upload writes the public
 * media.ggfix.in URL into that existing column and adds no schema at all. Same
 * outcome for storage, deployable against the live database as it stands, and the
 * same approach already used for owner KYC and shop artwork in auth-service.
 *
 * image_base64 is cleared on upload: it holds the legacy inline data URI, and leaving
 * it would keep a multi-megabyte copy in the row after the point of this change was
 * to get it out.
 */
@Service
public class TaxonomyMediaService {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyMediaService.class);

    private final MasterDeviceCategoryRepository categoryRepo;
    private final MasterBrandRepository brandRepo;
    private final MasterBannerRepository bannerRepo;
    private final ModelCompatibilityRepository compatibilityRepo;
    private final MediaUploadValidator validator;
    private final S3StorageService storage;
    private final TransactionTemplate tx;

    public TaxonomyMediaService(MasterDeviceCategoryRepository categoryRepo,
                                MasterBrandRepository brandRepo,
                                MasterBannerRepository bannerRepo,
                                ModelCompatibilityRepository compatibilityRepo,
                                MediaUploadValidator validator,
                                S3StorageService storage,
                                TransactionTemplate tx) {
        this.categoryRepo = categoryRepo;
        this.brandRepo = brandRepo;
        this.bannerRepo = bannerRepo;
        this.compatibilityRepo = compatibilityRepo;
        this.validator = validator;
        this.storage = storage;
        this.tx = tx;
    }

    /** Upload or replace a category tile. */
    public TaxonomyImageResponse uploadCategoryImage(UUID categoryId, MultipartFile file) {
        MasterDeviceCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new MediaValidationException("No category with id " + categoryId + "."));

        MediaUploadValidator.ValidatedUpload upload = validator.validateImage(file);
        String key = MediaKeys.masterCategoryImageKey(category.getName(), upload.extension());

        // Upload before the update: an unreferenced object is sweepable, whereas a row
        // pointing at a missing key is a broken tile on the customer Home screen.
        String url = storage.put(key, upload.bytes(), upload.contentType());

        try {
            return tx.execute(status -> {
                MasterDeviceCategory row = categoryRepo.findById(categoryId).orElseThrow();
                row.setImageUrl(url);
                row.setImageBase64(null);
                MasterDeviceCategory saved = categoryRepo.save(row);
                log.info("Category {} image -> {}", categoryId, key);
                return new TaxonomyImageResponse(saved.getId(), saved.getName(), key, saved.getImageUrl(),
                        upload.originalName(), upload.contentType(), upload.size());
            });
        } catch (RuntimeException e) {
            log.error("Category {} update failed after upload; removing orphaned object {}", categoryId, key, e);
            storage.deleteQuietly(key);
            throw e;
        }
    }

    /** Upload or replace a brand logo. */
    public TaxonomyImageResponse uploadBrandImage(UUID brandId, MultipartFile file) {
        MasterBrand brand = brandRepo.findById(brandId)
                .orElseThrow(() -> new MediaValidationException("No brand with id " + brandId + "."));

        MediaUploadValidator.ValidatedUpload upload = validator.validateImage(file);
        String key = MediaKeys.masterBrandImageKey(brand.getName(), upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());

        try {
            return tx.execute(status -> {
                MasterBrand row = brandRepo.findById(brandId).orElseThrow();
                row.setImageUrl(url);
                row.setImageBase64(null);
                MasterBrand saved = brandRepo.save(row);
                log.info("Brand {} image -> {}", brandId, key);
                return new TaxonomyImageResponse(saved.getId(), saved.getName(), key, saved.getImageUrl(),
                        upload.originalName(), upload.contentType(), upload.size());
            });
        } catch (RuntimeException e) {
            log.error("Brand {} update failed after upload; removing orphaned object {}", brandId, key, e);
            storage.deleteQuietly(key);
            throw e;
        }
    }

    /**
     * Upload or replace a home-screen banner image.
     *
     * Keyed on the banner's title — {@code banner/slider-1-8ab31f04.jpg} — so the
     * bucket reads the way the admin list does. A banner with no title cannot be
     * keyed, and is rejected rather than filed under something invented.
     */
    public TaxonomyImageResponse uploadBannerImage(UUID bannerId, MultipartFile file) {
        MasterBanner banner = bannerRepo.findById(bannerId)
                .orElseThrow(() -> new MediaValidationException("No banner with id " + bannerId + "."));
        if (banner.getTitle() == null || banner.getTitle().isBlank()) {
            throw new MediaValidationException(
                    "This banner has no title, so its image cannot be named. Set a title first.");
        }

        MediaUploadValidator.ValidatedUpload upload = validator.validateImage(file);
        String key = MediaKeys.bannerImageKey(banner.getTitle(), upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());

        try {
            return tx.execute(status -> {
                MasterBanner row = bannerRepo.findById(bannerId).orElseThrow();
                row.setImageUrl(url);
                row.setImageBase64(null);
                MasterBanner saved = bannerRepo.save(row);
                log.info("Banner {} image -> {}", bannerId, key);
                return new TaxonomyImageResponse(saved.getId(), saved.getTitle(), key, saved.getImageUrl(),
                        upload.originalName(), upload.contentType(), upload.size());
            });
        } catch (RuntimeException e) {
            log.error("Banner {} update failed after upload; removing orphaned object {}", bannerId, key, e);
            storage.deleteQuietly(key);
            throw e;
        }
    }

    /**
     * Upload or replace the reference photo on a Model Compatibility box.
     *
     * Keyed on the box NUMBER — {@code master/model-compatibility/a-12-9d3f7b10.jpg} —
     * which migration 79 keeps unique, so the bucket browses the way the shelf is
     * labelled. Id-scoped like the others: the box has to be saved before its photo
     * can be named, which is why the admin form uploads after the save rather than
     * before it.
     */
    public TaxonomyImageResponse uploadCompatibilityImage(UUID compatibilityId, MultipartFile file) {
        ModelCompatibility box = compatibilityRepo.findById(compatibilityId)
                .orElseThrow(() -> new MediaValidationException(
                        "No compatibility box with id " + compatibilityId + "."));
        if (box.getBoxNo() == null || box.getBoxNo().isBlank()) {
            throw new MediaValidationException(
                    "This box has no Box No, so its image cannot be named. Set a Box No first.");
        }

        MediaUploadValidator.ValidatedUpload upload = validator.validateImage(file);
        String key = MediaKeys.modelCompatibilityImageKey(box.getBoxNo(), upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());

        try {
            return tx.execute(status -> {
                ModelCompatibility row = compatibilityRepo.findById(compatibilityId).orElseThrow();
                row.setReferenceImageUrl(url);
                ModelCompatibility saved = compatibilityRepo.save(row);
                log.info("Compatibility box {} image -> {}", compatibilityId, key);
                return new TaxonomyImageResponse(saved.getId(), saved.getBoxNo(), key,
                        saved.getReferenceImageUrl(),
                        upload.originalName(), upload.contentType(), upload.size());
            });
        } catch (RuntimeException e) {
            log.error("Compatibility box {} update failed after upload; removing orphaned object {}",
                    compatibilityId, key, e);
            storage.deleteQuietly(key);
            throw e;
        }
    }

    /*
     * The superseded object is deliberately NOT deleted. Without image_key stored we
     * cannot prove which key the old URL referred to, and deriving it from the URL
     * would delete the wrong object if the public base ever changes. A stale object
     * costs a few KB; deleting a live one breaks a tile. A prefix sweep of
     * master/categories and master/brands can reclaim them later.
     */
}
