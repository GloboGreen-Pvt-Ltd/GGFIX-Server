package com.repairshop.saas.common.media;

import java.util.Arrays;
import java.util.Locale;

/**
 * Every file type accepted anywhere under media.ggfix.in, paired with the canonical
 * MIME type and extension it is stored as, and with the signature that proves it.
 *
 * Detection is by magic bytes, never by the declared Content-Type: that header is
 * client-supplied and trivially forged, and these objects are served straight to
 * browsers from the CDN. Storing the extension from the DETECTED type is what makes
 * "photo.JPG", "photo.jpeg" and "photo" all land on ".jpg".
 *
 * Which subset a given upload may use is the validator's decision, not this enum's —
 * device images allow only the three image types, while GST and Udyam certificates
 * are commonly issued as PDFs.
 */
public enum MediaFileType {

    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true),
    /** Certificates (GST, Udyam) are routinely PDFs; never allowed for artwork. */
    PDF("application/pdf", "pdf", false);

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_MAGIC = { 'W', 'E', 'B', 'P' };
    private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };

    private final String contentType;
    private final String extension;
    private final boolean image;

    MediaFileType(String contentType, String extension, boolean image) {
        this.contentType = contentType;
        this.extension = extension;
        this.image = image;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public boolean isImage() {
        return image;
    }

    /**
     * Identify a file from its leading bytes.
     *
     * @return the type, or {@code null} if the bytes match nothing we accept
     */
    public static MediaFileType detect(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (startsWith(bytes, JPEG_MAGIC)) {
            return JPEG;
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return PNG;
        }
        if (startsWith(bytes, PDF_MAGIC)) {
            return PDF;
        }
        // WebP is a RIFF container: "RIFF" <4-byte length> "WEBP", so the bytes that
        // identify it sit at offset 8, not at the start.
        if (bytes.length >= 12
                && startsWith(bytes, RIFF_MAGIC)
                && Arrays.equals(bytes, 8, 12, WEBP_MAGIC, 0, 4)) {
            return WEBP;
        }
        return null;
    }

    /** Human list for error messages, e.g. "JPEG, PNG, WebP". */
    public static String describe(java.util.Collection<MediaFileType> allowed) {
        return allowed.stream()
                .map(t -> switch (t) {
                    case JPEG -> "JPEG";
                    case PNG -> "PNG";
                    case WEBP -> "WebP";
                    case PDF -> "PDF";
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse("none");
    }

    static String normaliseExtension(String extension) {
        return extension == null ? "" : extension.toLowerCase(Locale.ROOT);
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
