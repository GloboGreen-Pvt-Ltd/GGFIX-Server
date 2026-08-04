package com.repairshop.saas.masterdata.service;

import com.repairshop.saas.common.media.MediaKeys;
import com.repairshop.saas.common.media.MediaProperties;
import com.repairshop.saas.common.media.MediaUploadValidator;
import com.repairshop.saas.common.media.MediaValidationException;
import com.repairshop.saas.common.media.S3StorageService;
import com.repairshop.saas.masterdata.dto.TaxonomyImageResponse;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Category and brand artwork on media.ggfix.in.
 *
 * Same ordering and compensation as {@link ModelMediaService}: upload, commit,
 * delete-on-failure, and remove the superseded object only after the commit. See
 * that class for why the database work runs through a {@link TransactionTemplate}
 * rather than {@code @Transactional}.
 *
 * These keys are flat — {@code master/categories/audio-device-1f0ab993.jpg} — because
 * a category has no parent to nest beneath, unlike a model which hangs off its
 * category/brand/series.
 *
 * Uploading also CLEARS imageUrl and imageBase64. Those columns hold the legacy
 * inline data URI, and clients that prefer imageUrl would otherwise keep rendering
 * the old inlined copy and never read the new key.
 */
@Service
public class TaxonomyMediaService {

    private static final Logger log = LoggerFactory.getLogger(TaxonomyMediaService.class);

    private final MasterDeviceCategoryRepository categoryRepo;
    private final MasterBrandRepository brandRepo;
    private final MediaUploadValidator validator;
    private final S3StorageService storage;
    private final MediaProperties props;
    private final TransactionTemplate tx;

    public TaxonomyMediaService(MasterDeviceCategoryRepository categoryRepo,
                                MasterBrandRepository brandRepo,
                                MediaUploadValidator validator,
                                S3StorageService storage,
                                MediaProperties props,
                                TransactionTemplate tx) {
        this.categoryRepo = categoryRepo;
        this.brandRepo = brandRepo;
        this.validator = validator;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
    }

    /** Upload or replace a category tile. */
    public TaxonomyImageResponse uploadCategoryImage(UUID categoryId, MultipartFile file) {
        MasterDeviceCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new MediaValidationException("No category with id " + categoryId + "."));

        return store(file,
                ext -> MediaKeys.masterCategoryImageKey(category.getName(), ext),
                category.getImageKey(),
                (key, upload) -> tx.execute(status -> {
                    MasterDeviceCategory row = categoryRepo.findById(categoryId).orElseThrow();
                    row.setImageKey(key);
                    row.setImageOriginalName(upload.originalName());
                    row.setImageContentType(upload.contentType());
                    row.setImageSizeBytes(upload.size());
                    row.setImageUrl(null);
                    row.setImageBase64(null);
                    MasterDeviceCategory saved = categoryRepo.save(row);
                    return new TaxonomyImageResponse(saved.getId(), saved.getName(), saved.getImageKey(),
                            props.publicUrl(saved.getImageKey()), saved.getImageOriginalName(),
                            saved.getImageContentType(), saved.getImageSizeBytes());
                }),
                key -> categoryRepo.existsByImageKey(key));
    }

    /** Upload or replace a brand logo. */
    public TaxonomyImageResponse uploadBrandImage(UUID brandId, MultipartFile file) {
        MasterBrand brand = brandRepo.findById(brandId)
                .orElseThrow(() -> new MediaValidationException("No brand with id " + brandId + "."));

        return store(file,
                ext -> MediaKeys.masterBrandImageKey(brand.getName(), ext),
                brand.getImageKey(),
                (key, upload) -> tx.execute(status -> {
                    MasterBrand row = brandRepo.findById(brandId).orElseThrow();
                    row.setImageKey(key);
                    row.setImageOriginalName(upload.originalName());
                    row.setImageContentType(upload.contentType());
                    row.setImageSizeBytes(upload.size());
                    row.setImageUrl(null);
                    row.setImageBase64(null);
                    MasterBrand saved = brandRepo.save(row);
                    return new TaxonomyImageResponse(saved.getId(), saved.getName(), saved.getImageKey(),
                            props.publicUrl(saved.getImageKey()), saved.getImageOriginalName(),
                            saved.getImageContentType(), saved.getImageSizeBytes());
                }),
                key -> brandRepo.existsByImageKey(key));
    }

    /** Builds the key from the validated extension. */
    private interface KeyBuilder {
        String build(String extension);
    }

    /** Persists the row and returns the response, inside a transaction. */
    private interface Committer {
        TaxonomyImageResponse commit(String key, MediaUploadValidator.ValidatedUpload upload);
    }

    /**
     * The shared upload → commit → compensate sequence. Categories and brands differ
     * only in which repository and key builder they use, so the ordering rules — the
     * part that is easy to get subtly wrong — live in exactly one place.
     */
    private TaxonomyImageResponse store(MultipartFile file,
                                        KeyBuilder keyBuilder,
                                        String previousKey,
                                        Committer committer,
                                        java.util.function.Predicate<String> stillReferenced) {
        MediaUploadValidator.ValidatedUpload upload = validator.validateImage(file);
        String key = keyBuilder.build(upload.extension());

        // Upload first: an unreferenced object is sweepable, whereas a row pointing
        // at a missing key is a broken tile on the customer Home screen.
        storage.put(key, upload.bytes(), upload.contentType());

        TaxonomyImageResponse response;
        try {
            response = committer.commit(key, upload);
        } catch (RuntimeException e) {
            log.error("Taxonomy image insert failed after upload; removing orphaned object {}", key, e);
            storage.deleteQuietly(key);
            throw e;
        }

        // Committed, so the old object is now unreferenced and safe to remove.
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(key)
                && !stillReferenced.test(previousKey)) {
            storage.deleteQuietly(previousKey);
        }
        return response;
    }
}
