package com.repairshop.saas.common.media;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

/**
 * Validates an upload and resolves the extension it will be stored under.
 *
 * The declared Content-Type is never trusted — it is client-supplied and forgeable,
 * and everything here is served straight to browsers from media.ggfix.in. The type
 * comes from the file's magic bytes ({@link MediaFileType#detect}), and the declared
 * value only has to be consistent with what was found. That keeps an executable
 * renamed to .jpg out of the bucket.
 *
 * Two entry points because the allowed sets genuinely differ: artwork must be an
 * image, whereas GST and Udyam certificates are routinely issued as PDFs. Sharing
 * one method with a boolean would hide which callers accept PDFs.
 */
@Component
public class MediaUploadValidator {

    private static final Set<MediaFileType> IMAGES =
            EnumSet.of(MediaFileType.JPEG, MediaFileType.PNG, MediaFileType.WEBP);
    private static final Set<MediaFileType> DOCUMENTS =
            EnumSet.of(MediaFileType.JPEG, MediaFileType.PNG, MediaFileType.WEBP, MediaFileType.PDF);

    private final MediaProperties props;

    public MediaUploadValidator(MediaProperties props) {
        this.props = props;
    }

    /**
     * A file that passed validation.
     *
     * @param type         format proven by the file's own bytes
     * @param bytes        the full contents, read once
     * @param originalName the uploaded filename, for display and audit only
     */
    public record ValidatedUpload(MediaFileType type, byte[] bytes, String originalName) {

        public String contentType() {
            return type.contentType();
        }

        public String extension() {
            return type.extension();
        }

        public long size() {
            return bytes.length;
        }
    }

    /** Artwork: device images, category and brand tiles, shop fronts and banners. */
    public ValidatedUpload validateImage(MultipartFile file) {
        return validate(file, IMAGES, props.getMaxImageBytes());
    }

    /** KYC and certificates: the image types plus PDF, at the larger document limit. */
    public ValidatedUpload validateDocument(MultipartFile file) {
        return validate(file, DOCUMENTS, props.getMaxDocumentBytes());
    }

    private ValidatedUpload validate(MultipartFile file, Set<MediaFileType> allowed, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("No file was supplied.");
        }
        // Checked before reading so an oversized upload is rejected without pulling
        // the whole thing into heap. Spring's multipart limit is the outer guard;
        // this is the per-kind policy and is deliberately stricter.
        if (file.getSize() > maxBytes) {
            throw new MediaValidationException(
                    "File is %.2f MB; the maximum is %.2f MB."
                            .formatted(file.getSize() / 1048576.0, maxBytes / 1048576.0));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Could not read the uploaded file.", e);
        }
        if (bytes.length == 0) {
            throw new MediaValidationException("The uploaded file is empty.");
        }

        MediaFileType detected = MediaFileType.detect(bytes);
        if (detected == null || !allowed.contains(detected)) {
            throw new MediaValidationException(
                    "Unsupported file format. Allowed formats are " + MediaFileType.describe(allowed) + ".");
        }

        // A mismatch means the browser and the bytes disagree; treat that as hostile
        // rather than quietly preferring one. Absent or generic types are tolerated,
        // because some clients send application/octet-stream for a genuine image.
        String declared = file.getContentType();
        if (declared != null
                && !declared.isBlank()
                && (declared.startsWith("image/") || declared.equals("application/pdf"))
                && !declared.equalsIgnoreCase(detected.contentType())) {
            throw new MediaValidationException(
                    "The file claims to be %s but its contents are %s."
                            .formatted(declared, detected.contentType()));
        }

        return new ValidatedUpload(detected, bytes, file.getOriginalFilename());
    }
}
