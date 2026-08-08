package com.repairshop.saas.masterdata.controller;

import com.repairshop.saas.common.media.MediaKeys;
import com.repairshop.saas.common.media.MediaProperties;
import com.repairshop.saas.common.media.MediaUploadValidator;
import com.repairshop.saas.common.media.S3StorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The shared upload endpoint every app posts to — device files on a booking,
 * complaint voice notes, and anything else that needs bytes hosted without an
 * owning catalogue row.
 *
 * <h2>S3 only</h2>
 * This used to sign uploads to Cloudinary, and returned 503 when its credentials
 * were absent. They were never set on this deployment, so every device-file
 * upload failed with "Image hosting is not configured" while the S3 bucket sat
 * unused — it was wired only to the id-scoped artwork endpoints. Cloudinary is
 * gone: uploads land in the same bucket, behind the same CDN, as every other
 * object under media.ggfix.in.
 *
 * <h2>The caller names a folder, never a path</h2>
 * {@code folder} and {@code slot} are slugified into the key by
 * {@link MediaKeys#uploadKey}, so a client cannot choose where its bytes land or
 * climb out of the prefix it was given. The type is proven by the file's magic
 * bytes, not its declared Content-Type, and the stored extension comes from what
 * was detected — the same rules the artwork endpoints already follow.
 */
@RestController
@RequestMapping("/media")
public class MediaController {

    private final MediaUploadValidator validator;
    private final S3StorageService storage;
    private final MediaProperties props;

    public MediaController(MediaUploadValidator validator, S3StorageService storage, MediaProperties props) {
        this.validator = validator;
        this.storage = storage;
        this.props = props;
    }

    /**
     * Health check. Hitting GET /media/ping confirms the service is running the
     * S3 build and whether a bucket is configured — the two things worth knowing
     * before debugging an upload.
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("controller", "MediaController");
        out.put("storage", "s3");
        boolean configured = props.getBucket() != null && !props.getBucket().isBlank();
        out.put("bucket", configured ? props.getBucket() : "NOT-configured (uploads disabled)");
        out.put("publicBaseUrl", props.getPublicBaseUrl());
        return ResponseEntity.ok(out);
    }

    /**
     * <pre>
     * POST /media/upload            (multipart/form-data)
     *   file    the bytes  (jpeg | png | webp | mp4 | webm | m4a | mp3)
     *   folder  optional destination prefix, e.g. "Devicefiles"
     *   slot    optional filename stem,      e.g. "front" | "back" | "video"
     * </pre>
     *
     * Returns the public URL plus the key it landed on. The response keeps the
     * {@code url} field the apps already read, so no client needs changing.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "folder", required = false) String folder,
                                                      @RequestParam(value = "slot", required = false) String slot) {
        // Throws MediaValidationException (400) or MediaStorageException (502),
        // both already turned into readable JSON by MediaExceptionHandler.
        MediaUploadValidator.ValidatedUpload upload = validator.validateMedia(file);
        String key = MediaKeys.uploadKey(folder, slot, upload.extension());
        String url = storage.put(key, upload.bytes(), upload.contentType());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("url", url);
        out.put("key", key);
        out.put("source", "s3");
        out.put("contentType", upload.contentType());
        out.put("bytes", upload.size());
        out.put("originalName", upload.originalName());
        return ResponseEntity.ok(out);
    }
}
