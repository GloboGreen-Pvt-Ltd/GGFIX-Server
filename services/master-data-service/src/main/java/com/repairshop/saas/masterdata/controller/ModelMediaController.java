package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.masterdata.dto.ModelCreateForm;
import com.repairshop.saas.masterdata.dto.ModelImageResponse;
import com.repairshop.saas.masterdata.dto.TaxonomyImageResponse;
import com.repairshop.saas.masterdata.service.ModelMediaService;
import com.repairshop.saas.masterdata.service.TaxonomyMediaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Model images on media.ggfix.in.
 *
 * The client posts IDs and a file; the S3 key is derived server-side from the names
 * those IDs resolve to. There is deliberately no way for a caller to supply a path,
 * a folder or a filename — see {@link com.repairshop.saas.common.media.MediaKeys}.
 *
 * Both endpoints are POST. Replacement is semantically a PUT, but Tomcat only parses
 * multipart bodies on POST unless casual multipart parsing is switched on, and
 * quietly receiving an empty file part is a worse failure than an inexact verb.
 */
@RestController
@RequestMapping("/master")
public class ModelMediaController {

    private final ModelMediaService service;
    private final TaxonomyMediaService taxonomyService;

    public ModelMediaController(ModelMediaService service, TaxonomyMediaService taxonomyService) {
        this.service = service;
        this.taxonomyService = taxonomyService;
    }

    /**
     * Create a model and upload its image in one request.
     *
     * <pre>
     * POST /master/models/with-image      (multipart/form-data)
     *   categoryId  UUID
     *   brandId     UUID
     *   seriesId    UUID
     *   modelName   text
     *   image       file  (jpeg | png | webp)
     * </pre>
     *
     * 201 with the saved keys and the public URL.
     */
    @PostMapping(value = "/models/with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelImageResponse> createWithImage(@ModelAttribute ModelCreateForm form,
                                                              @RequestParam("image") MultipartFile image) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createWithImage(form, image));
    }

    /**
     * Replace an existing model's image, keeping its folder.
     *
     * <pre>
     * POST /master/models/{id}/image      (multipart/form-data)
     *   image       file  (jpeg | png | webp)
     * </pre>
     *
     * The response carries a NEW imageKey — the filename changes on every upload so
     * CloudFront and the browser cannot serve the superseded image.
     */
    @PostMapping(value = "/models/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ModelImageResponse> replaceImage(@PathVariable UUID id,
                                                           @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(service.replaceImage(id, image));
    }

    /**
     * Preview the folder a model would be filed under, without uploading anything.
     *
     * Lets the admin form show "this will be saved to mobile/vivo/y-series/vivo-y20"
     * as the user picks, and makes the shared base path visible: every model under
     * one category/brand/series returns the same first three segments.
     */
    @PostMapping(value = "/models/media-path/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> previewPath(@org.springframework.web.bind.annotation.RequestBody
                                                           ModelCreateForm form) {
        return ResponseEntity.ok(service.previewFolder(form));
    }

    /**
     * Upload or replace a category tile.
     *
     * <pre>
     * POST /master/device-categories/{id}/image   (multipart/form-data)
     *   image  file  (jpeg | png | webp)
     * </pre>
     *
     * Replaces the legacy path where the admin inlined the file as a base64 data URI
     * into image_url when Cloudinary was unconfigured — the row now stores a key and
     * the bytes live in S3.
     */
    @PostMapping(value = "/device-categories/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaxonomyImageResponse> uploadCategoryImage(@PathVariable UUID id,
                                                                     @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(taxonomyService.uploadCategoryImage(id, image));
    }

    /**
     * Upload or replace a brand logo.
     *
     * <pre>
     * POST /master/brands/{id}/image   (multipart/form-data)
     *   image  file  (jpeg | png | webp)
     * </pre>
     */
    @PostMapping(value = "/brands/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaxonomyImageResponse> uploadBrandImage(@PathVariable UUID id,
                                                                  @RequestParam("image") MultipartFile image) {
        return ResponseEntity.ok(taxonomyService.uploadBrandImage(id, image));
    }
}
