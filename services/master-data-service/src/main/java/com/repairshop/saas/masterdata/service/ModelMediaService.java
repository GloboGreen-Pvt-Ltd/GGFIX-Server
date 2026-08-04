package com.repairshop.saas.masterdata.service;

import com.repairshop.saas.masterdata.dto.ModelCreateForm;
import com.repairshop.saas.masterdata.dto.ModelImageResponse;
import com.repairshop.saas.masterdata.entity.MasterBrand;
import com.repairshop.saas.masterdata.entity.MasterDeviceCategory;
import com.repairshop.saas.masterdata.entity.MasterDeviceSeries;
import com.repairshop.saas.masterdata.entity.MasterModel;
import com.repairshop.saas.masterdata.media.ImageValidator;
import com.repairshop.saas.masterdata.media.MediaKeys;
import com.repairshop.saas.masterdata.media.MediaProperties;
import com.repairshop.saas.masterdata.media.MediaValidationException;
import com.repairshop.saas.masterdata.media.S3StorageService;
import com.repairshop.saas.masterdata.media.Slugify;
import com.repairshop.saas.masterdata.repository.MasterBrandRepository;
import com.repairshop.saas.masterdata.repository.MasterCategoryBrandMappingRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceCategoryRepository;
import com.repairshop.saas.masterdata.repository.MasterDeviceSeriesRepository;
import com.repairshop.saas.masterdata.repository.MasterModelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates models with an image, and replaces the image on an existing model.
 *
 * <h2>Why the ordering is what it is</h2>
 * S3 is not transactional, so the two stores are reconciled by ordering the work and
 * compensating on failure rather than by wrapping them together:
 *
 * <ol>
 *   <li>validate the file and resolve the taxonomy — no side effects yet;</li>
 *   <li>PUT the object, so the row is never written pointing at a key that does not
 *       exist (a broken image in the catalogue is worse than an unreferenced object
 *       in the bucket);</li>
 *   <li>commit the row;</li>
 *   <li>if the commit failed, delete the object just uploaded;</li>
 *   <li>on replacement only, delete the superseded object — strictly after the
 *       commit, so a rollback can never leave the row pointing at bytes we removed.</li>
 * </ol>
 *
 * The database work runs through an explicit {@link TransactionTemplate} rather than
 * {@code @Transactional}. Two reasons: the S3 calls must sit OUTSIDE the transaction
 * (holding a connection open across a network upload would exhaust the 5-connection
 * Hikari pool under any concurrency), and the "delete only after the update succeeds"
 * rule means after COMMIT, which a method-level annotation cannot express — the
 * commit happens as the annotated method returns, so a delete inside it would run
 * too early.
 */
@Service
public class ModelMediaService {

    private static final Logger log = LoggerFactory.getLogger(ModelMediaService.class);

    private final MasterModelRepository modelRepo;
    private final MasterBrandRepository brandRepo;
    private final MasterDeviceCategoryRepository categoryRepo;
    private final MasterDeviceSeriesRepository seriesRepo;
    private final MasterCategoryBrandMappingRepository mappingRepo;
    private final ImageValidator validator;
    private final S3StorageService storage;
    private final MediaProperties props;
    private final TransactionTemplate tx;

    public ModelMediaService(MasterModelRepository modelRepo,
                             MasterBrandRepository brandRepo,
                             MasterDeviceCategoryRepository categoryRepo,
                             MasterDeviceSeriesRepository seriesRepo,
                             MasterCategoryBrandMappingRepository mappingRepo,
                             ImageValidator validator,
                             S3StorageService storage,
                             MediaProperties props,
                             TransactionTemplate tx) {
        this.modelRepo = modelRepo;
        this.brandRepo = brandRepo;
        this.categoryRepo = categoryRepo;
        this.seriesRepo = seriesRepo;
        this.mappingRepo = mappingRepo;
        this.validator = validator;
        this.storage = storage;
        this.props = props;
        this.tx = tx;
    }

    /** Resolved names for the four key segments, proven to form a valid hierarchy. */
    private record Taxonomy(MasterDeviceCategory category, MasterBrand brand, MasterDeviceSeries series) {
    }

    // ------------------------------------------------------------------ create --

    public ModelImageResponse createWithImage(ModelCreateForm form, MultipartFile file) {
        ImageValidator.ValidatedImage image = validator.validate(file);

        String modelName = form.getModelName() == null ? "" : form.getModelName().trim();
        if (modelName.isEmpty()) {
            throw new MediaValidationException("Model name is required.");
        }
        Taxonomy taxonomy = resolveAndValidate(form.getCategoryId(), form.getBrandId(), form.getSeriesId());

        if (modelRepo.existsByBrandIdAndNameIgnoreCase(taxonomy.brand().getId(), modelName)) {
            throw new MediaValidationException(
                    "A model named '" + modelName + "' already exists for " + taxonomy.brand().getName() + ".");
        }

        String folderKey = MediaKeys.modelFolder(
                taxonomy.category().getName(),
                taxonomy.brand().getName(),
                taxonomy.series().getName(),
                modelName);
        String imageKey = MediaKeys.modelImageKey(folderKey, image.extension());

        // Upload BEFORE the insert: an object with no row is invisible and sweepable,
        // a row with no object is a broken image on every storefront that renders it.
        storage.put(imageKey, image.bytes(), image.contentType());

        MasterModel saved;
        try {
            saved = tx.execute(status -> {
                MasterModel model = MasterModel.builder()
                        .brandId(taxonomy.brand().getId())
                        .categoryId(taxonomy.category().getId())
                        .seriesId(taxonomy.series().getId())
                        .name(modelName)
                        .slug(Slugify.requireSlug(modelName, "model name"))
                        // Inline jsonb arrays (migrations 69/70/73); master_model_variants
                        // was dropped in 71, so these belong on the same insert.
                        .modelNumber(nullSafe(form.getModelNumber()))
                        .colors(nullSafe(form.getColors()))
                        .ramStorage(nullSafe(form.getRamStorage()))
                        .sellActive(form.getSellActive() == null ? Boolean.TRUE : form.getSellActive())
                        .mediaFolderKey(folderKey)
                        .imageKey(imageKey)
                        .imageOriginalName(image.originalName())
                        .imageContentType(image.contentType())
                        .imageSizeBytes(image.size())
                        .build();
                return modelRepo.save(model);
            });
        } catch (RuntimeException e) {
            // Requirement: S3 succeeded but the database did not — take the object
            // back out so a retry does not accumulate orphans.
            log.error("Model insert failed after upload; removing orphaned object {}", imageKey, e);
            storage.deleteQuietly(imageKey);
            throw e;
        }

        return toResponse(saved);
    }

    // ----------------------------------------------------------------- replace --

    /**
     * Replace the image on an existing model, keeping its folder.
     *
     * The previous object is removed only once the new key is committed, so an
     * interrupted replacement degrades to "still showing the old image" rather than
     * to a model with no image at all.
     */
    public ModelImageResponse replaceImage(UUID modelId, MultipartFile file) {
        ImageValidator.ValidatedImage image = validator.validate(file);

        MasterModel existing = modelRepo.findById(modelId)
                .orElseThrow(() -> new MediaValidationException("No model with id " + modelId + "."));

        // Legacy rows (Cloudinary-era) have no folder yet; derive it now from the
        // taxonomy they already carry so they migrate on first replacement.
        String folderKey = existing.getMediaFolderKey();
        if (folderKey == null || folderKey.isBlank()) {
            Taxonomy taxonomy = resolveAndValidate(
                    existing.getCategoryId(), existing.getBrandId(), existing.getSeriesId());
            folderKey = MediaKeys.modelFolder(
                    taxonomy.category().getName(),
                    taxonomy.brand().getName(),
                    taxonomy.series().getName(),
                    existing.getName());
        }

        String previousKey = existing.getImageKey();
        String newKey = MediaKeys.modelImageKey(folderKey, image.extension());
        String finalFolderKey = folderKey;

        storage.put(newKey, image.bytes(), image.contentType());

        MasterModel saved;
        try {
            saved = tx.execute(status -> {
                MasterModel model = modelRepo.findById(modelId)
                        .orElseThrow(() -> new MediaValidationException("No model with id " + modelId + "."));
                model.setMediaFolderKey(finalFolderKey);
                model.setImageKey(newKey);
                model.setImageOriginalName(image.originalName());
                model.setImageContentType(image.contentType());
                model.setImageSizeBytes(image.size());
                // The Cloudinary URL and any inline base64 are now stale and would
                // otherwise keep winning in clients that prefer image_url.
                model.setImageUrl(null);
                model.setImageBase64(null);
                return modelRepo.save(model);
            });
        } catch (RuntimeException e) {
            log.error("Model {} update failed after upload; removing orphaned object {}", modelId, newKey, e);
            storage.deleteQuietly(newKey);
            throw e;
        }

        // Committed. Only now is the old object unreferenced and safe to remove.
        if (previousKey != null && !previousKey.isBlank() && !previousKey.equals(newKey)) {
            if (modelRepo.findByImageKey(previousKey).isEmpty()) {
                storage.deleteQuietly(previousKey);
            } else {
                // Defensive: the unique index should make this impossible, but a
                // delete is irreversible and sharing would mean breaking another row.
                log.warn("Kept previous object {} — another model still references it.", previousKey);
            }
        }

        return toResponse(saved);
    }

    // ----------------------------------------------------------------- preview --

    /**
     * The folder a model would be filed under, computed without touching S3.
     *
     * Makes the shared base path explicit for the admin UI: {@code baseFolder} is
     * identical for every model in one category/brand/series, and only
     * {@code modelFolder} and the eventual filename differ between them. Model name
     * is optional here so the form can show the base as soon as the three pickers
     * are set.
     */
    public Map<String, String> previewFolder(ModelCreateForm form) {
        Taxonomy taxonomy = resolveAndValidate(form.getCategoryId(), form.getBrandId(), form.getSeriesId());

        String baseFolder = String.join("/",
                Slugify.requireSlug(taxonomy.category().getName(), "category"),
                Slugify.requireSlug(taxonomy.brand().getName(), "brand"),
                Slugify.requireSlug(taxonomy.series().getName(), "series"));

        Map<String, String> out = new LinkedHashMap<>();
        out.put("baseFolder", baseFolder);

        String modelName = form.getModelName() == null ? "" : form.getModelName().trim();
        if (!modelName.isEmpty()) {
            String modelFolder = baseFolder + "/" + Slugify.requireSlug(modelName, "model name");
            out.put("modelFolder", modelFolder);
            // Illustrative only — the real leaf gets a fresh random suffix at upload.
            out.put("examplePublicUrl", props.publicUrl(modelFolder + "/main-<id>.jpg"));
        }
        return out;
    }

    // ---------------------------------------------------------------- internals --

    /**
     * Load the three names and prove the hierarchy the client submitted is real:
     * the brand must be mapped to the category, and the series must belong to that
     * brand. Without this a caller could pair any IDs and file a Samsung model under
     * {@code mobile/vivo/y-series}, which no later validation would catch because
     * the key is only ever derived, never compared.
     */
    private Taxonomy resolveAndValidate(UUID categoryId, UUID brandId, UUID seriesId) {
        if (categoryId == null || brandId == null || seriesId == null) {
            throw new MediaValidationException(
                    "categoryId, brandId and seriesId are all required to build the media path.");
        }

        MasterDeviceCategory category = categoryRepo.findById(categoryId)
                .orElseThrow(() -> new MediaValidationException("No category with id " + categoryId + "."));
        MasterBrand brand = brandRepo.findById(brandId)
                .orElseThrow(() -> new MediaValidationException("No brand with id " + brandId + "."));
        MasterDeviceSeries series = seriesRepo.findById(seriesId)
                .orElseThrow(() -> new MediaValidationException("No series with id " + seriesId + "."));

        if (!mappingRepo.existsByCategoryIdAndBrandId(categoryId, brandId)) {
            throw new MediaValidationException(
                    "Brand '" + brand.getName() + "' is not available under category '"
                            + category.getName() + "'.");
        }
        if (series.getBrandId() == null || !series.getBrandId().equals(brandId)) {
            throw new MediaValidationException(
                    "Series '" + series.getName() + "' does not belong to brand '" + brand.getName() + "'.");
        }
        return new Taxonomy(category, brand, series);
    }

    /**
     * Multipart binding leaves an omitted repeated field null rather than empty, and
     * the jsonb columns are declared NOT NULL with a [] default — so a null here
     * would fail the insert rather than mean "no colours".
     */
    private static java.util.List<String> nullSafe(java.util.List<String> values) {
        return values == null ? new java.util.ArrayList<>() : values;
    }

    private ModelImageResponse toResponse(MasterModel model) {
        return new ModelImageResponse(
                model.getId(),
                model.getName(),
                model.getSlug(),
                model.getMediaFolderKey(),
                model.getImageKey(),
                props.publicUrl(model.getImageKey()),
                model.getImageOriginalName(),
                model.getImageContentType(),
                model.getImageSizeBytes());
    }
}
