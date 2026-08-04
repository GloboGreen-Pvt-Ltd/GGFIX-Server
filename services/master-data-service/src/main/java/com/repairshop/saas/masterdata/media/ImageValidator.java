package com.repairshop.saas.masterdata.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;

/**
 * Validates an uploaded image and resolves the extension it will be stored under.
 *
 * The declared Content-Type is a client-supplied header and is trivially forged, so
 * it is not what we trust: the type is determined by sniffing the file's magic
 * bytes, and the declared value only has to be consistent with what was found. That
 * keeps a .exe renamed to .jpg out of a bucket that is served straight to browsers
 * from media.ggfix.in.
 *
 * The extension is then derived from the detected type rather than from the
 * uploaded filename, so "photo.JPG", "photo.jpeg" and "photo" all land on ".jpg".
 */
@Component
public class ImageValidator {

    /** Longest signature we inspect (WebP needs 12 bytes). */
    private static final int SNIFF_LENGTH = 12;

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC =
            { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_MAGIC = { 'W', 'E', 'B', 'P' };

    private final long maxBytes;

    public ImageValidator(@Value("${app.media.max-image-bytes:5242880}") long maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * The image formats we accept, each paired with the canonical extension and MIME
     * type we store. Kept as an enum so adding a format is one entry plus a
     * signature check, and so no caller can invent an extension of its own.
     */
    public enum ImageType {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp");

        private final String contentType;
        private final String extension;

        ImageType(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }
    }

    /**
     * Outcome of a successful validation.
     *
     * @param type         format proven by the file's own bytes
     * @param bytes        the full file contents, read once
     * @param originalName the uploaded filename, for display and audit only
     */
    public record ValidatedImage(ImageType type, byte[] bytes, String originalName) {

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

    /**
     * @throws MediaValidationException if the file is empty, too large, not one of
     *         the accepted formats, or declares a type that contradicts its bytes
     */
    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("No image file was supplied.");
        }
        // Checked before reading so an oversized upload is rejected without pulling
        // the whole thing into heap. Spring's multipart limit is the outer guard;
        // this is the per-image policy and is deliberately stricter.
        if (file.getSize() > maxBytes) {
            throw new MediaValidationException(
                    "Image is %.2f MB; the maximum is %.2f MB."
                            .formatted(file.getSize() / 1048576.0, maxBytes / 1048576.0));
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new MediaStorageException("Could not read the uploaded image.", e);
        }
        if (bytes.length == 0) {
            throw new MediaValidationException("The uploaded image is empty.");
        }

        ImageType detected = detectType(bytes);
        if (detected == null) {
            throw new MediaValidationException(
                    "Unsupported image format. Allowed formats are JPEG, PNG and WebP.");
        }

        // A mismatch means the browser and the bytes disagree; treat it as hostile
        // rather than quietly preferring one. Absent/generic types are tolerated
        // because some clients send application/octet-stream for a genuine image.
        String declared = file.getContentType();
        if (declared != null
                && !declared.isBlank()
                && declared.startsWith("image/")
                && !declared.equalsIgnoreCase(detected.contentType())) {
            throw new MediaValidationException(
                    "The file claims to be %s but its contents are %s."
                            .formatted(declared, detected.contentType()));
        }

        return new ValidatedImage(detected, bytes, file.getOriginalFilename());
    }

    /** @return the detected type, or {@code null} if the bytes match nothing we allow */
    private static ImageType detectType(byte[] bytes) {
        if (bytes.length < 4) {
            return null;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return ImageType.JPEG;
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return ImageType.PNG;
        }
        // WebP is a RIFF container: "RIFF" <4-byte length> "WEBP".
        if (bytes.length >= SNIFF_LENGTH
                && startsWith(bytes, RIFF_MAGIC)
                && Arrays.equals(bytes, 8, 12, WEBP_MAGIC, 0, 4)) {
            return ImageType.WEBP;
        }
        return null;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
