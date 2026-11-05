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
    PDF("application/pdf", "pdf", false),

    /**
     * Device-file capture. A booking records the device as it arrives — front,
     * back and a walk-around video — and a complaint can carry a voice note, so
     * the generic upload endpoint has to accept more than artwork. Never allowed
     * for artwork, which is why isImage() stays false.
     */
    MP4("video/mp4", "mp4", false),
    WEBM("video/webm", "webm", false),
    M4A("audio/mp4", "m4a", false),
    MP3("audio/mpeg", "mp3", false);

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_MAGIC = { 'W', 'E', 'B', 'P' };
    private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };
    private static final byte[] FTYP_MAGIC = { 'f', 't', 'y', 'p' };
    private static final byte[] EBML_MAGIC = { (byte) 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 };
    private static final byte[] ID3_MAGIC = { 'I', 'D', '3' };

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
        // ISO base media (MP4 / MOV / M4A): "ftyp" sits at offset 4, followed by
        // a brand. The brand is what separates an audio-only .m4a from a video
        // .mp4 — both share the container, and storing an audio file as .mp4
        // makes players try to render a video track that isn't there.
        if (bytes.length >= 12 && Arrays.equals(bytes, 4, 8, FTYP_MAGIC, 0, 4)) {
            String brand = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                    .toLowerCase(Locale.ROOT);
            return brand.startsWith("m4a") || brand.startsWith("m4b") ? M4A : MP4;
        }
        // Matroska / WebM.
        if (startsWith(bytes, EBML_MAGIC)) {
            return WEBM;
        }
        // MP3: either an ID3 tag or a raw MPEG audio frame header.
        if (startsWith(bytes, ID3_MAGIC)) {
            return MP3;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0) {
            return MP3;
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
                    case MP4 -> "MP4";
                    case WEBM -> "WebM";
                    case M4A -> "M4A";
                    case MP3 -> "MP3";
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
