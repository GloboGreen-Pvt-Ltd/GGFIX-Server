package com.repairshop.saas.masterdata.media;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Validates anything posted to {@code /media/upload} — photo, video or voice note —
 * and resolves the extension and Content-Type it is stored under.
 *
 * The sibling {@link ImageValidator} covers catalogue artwork, which is images only
 * and small enough to hold in heap. This one covers every app upload, so it has to
 * accept video and audio, apply a separate ceiling to each kind, and must never read
 * the file into a byte array — a 40 MB video on a 160 MB heap is how the service
 * dies. It therefore reports only the *decision*; the bytes stay in the multipart
 * part and are streamed straight to S3 by {@link S3StorageService}.
 *
 * Format is proven by magic bytes, never by the declared Content-Type: the header is
 * client-supplied and trivially forged, and these objects are served straight to
 * browsers from media.ggfix.in. The stored extension follows the DETECTED type, so
 * "clip.MOV", "clip.mov" and "clip" all land on the same one.
 *
 * <h2>The one place the declared type is consulted</h2>
 * MPEG-4 and Matroska are containers that hold audio OR video, and the signature
 * cannot tell them apart without parsing the track boxes. A voice note recorded by
 * expo-av is an AAC track in an MPEG-4 container — byte-identical in its first 12
 * bytes to a video. So for those two containers only, a caller that says
 * {@code audio/*} gets {@code .m4a}/{@code .webm} with an audio Content-Type. The
 * container is still proven by the bytes; the hint only picks a label inside a family
 * already known to be safe, and the worst a lie achieves is an audio-typed video.
 */
@Component
public class UploadValidator {

    /** Longest signature we inspect: MP4/MOV needs 12 bytes (4 skipped + "ftyp" + brand). */
    private static final int SNIFF_LENGTH = 12;

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC =
            { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
    private static final byte[] RIFF_MAGIC = { 'R', 'I', 'F', 'F' };
    private static final byte[] WEBP_MAGIC = { 'W', 'E', 'B', 'P' };
    private static final byte[] WAVE_MAGIC = { 'W', 'A', 'V', 'E' };
    private static final byte[] FTYP_MAGIC = { 'f', 't', 'y', 'p' };
    private static final byte[] MATROSKA_MAGIC = { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3 };
    private static final byte[] ID3_MAGIC = { 'I', 'D', '3' };
    private static final byte[] OGG_MAGIC = { 'O', 'g', 'g', 'S' };

    private final MediaProperties props;

    public UploadValidator(MediaProperties props) {
        this.props = props;
    }

    /** Which ceiling applies, and what {@code resourceType} the response reports. */
    public enum Kind {
        IMAGE, VIDEO, AUDIO
    }

    /**
     * Formats accepted, each paired with the canonical extension and MIME type we
     * store. Adding one is an entry plus a signature check, and no caller can invent
     * an extension of its own.
     *
     * The video entries are what the phone cameras produce: Android records MP4, iOS
     * records QuickTime, a browser records WebM. The audio entries are what expo-av
     * produces plus what a gallery pick can hand back.
     */
    public enum FileType {
        JPEG("image/jpeg", "jpg", Kind.IMAGE),
        PNG("image/png", "png", Kind.IMAGE),
        WEBP("image/webp", "webp", Kind.IMAGE),

        MP4("video/mp4", "mp4", Kind.VIDEO),
        MOV("video/quicktime", "mov", Kind.VIDEO),
        WEBM("video/webm", "webm", Kind.VIDEO),

        M4A("audio/mp4", "m4a", Kind.AUDIO),
        WEBA("audio/webm", "webm", Kind.AUDIO),
        MP3("audio/mpeg", "mp3", Kind.AUDIO),
        WAV("audio/wav", "wav", Kind.AUDIO),
        OGG("audio/ogg", "ogg", Kind.AUDIO);

        private final String contentType;
        private final String extension;
        private final Kind kind;

        FileType(String contentType, String extension, Kind kind) {
            this.contentType = contentType;
            this.extension = extension;
            this.kind = kind;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }

        public Kind kind() {
            return kind;
        }
    }

    /**
     * Outcome of a successful validation. Carries no bytes on purpose — see the class
     * comment; the caller streams {@link MultipartFile#getInputStream()} instead.
     *
     * @param type         format proven by the file's own bytes
     * @param size         byte count, as reported by the multipart part
     * @param originalName the uploaded filename, for display and audit only
     */
    public record ValidatedUpload(FileType type, long size, String originalName) {

        public String contentType() {
            return type.contentType();
        }

        public String extension() {
            return type.extension();
        }

        public Kind kind() {
            return type.kind();
        }

        /** Lower-case {@code image} / {@code video} / {@code audio}, for the response. */
        public String resourceType() {
            return type.kind().name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @throws MediaValidationException if the file is empty, over the ceiling for its
     *         kind, or not one of the accepted photo/video/audio formats
     */
    public ValidatedUpload validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MediaValidationException("No file was supplied.");
        }

        // Sniffed before the size check so the error names the real problem: a 30 MB
        // upload is fine if it is a video and rejected if it is a photo, and "that
        // image is too large" would be a lie in the first case.
        FileType detected = detectType(readHeader(file), file.getContentType());
        if (detected == null) {
            throw new MediaValidationException(
                    "Unsupported file format. Photos must be JPEG, PNG or WebP; "
                            + "videos MP4, MOV or WebM; audio M4A, MP3, WAV, OGG or WebM.");
        }

        long ceiling = ceilingFor(detected.kind());
        if (file.getSize() > ceiling) {
            throw new MediaValidationException(
                    "That %s is %.1f MB; the maximum is %.0f MB."
                            .formatted(label(detected.kind()), file.getSize() / 1048576.0,
                                    ceiling / 1048576.0));
        }

        return new ValidatedUpload(detected, file.getSize(), file.getOriginalFilename());
    }

    private long ceilingFor(Kind kind) {
        return switch (kind) {
            case IMAGE -> props.getMaxImageBytes();
            case VIDEO -> props.getMaxVideoBytes();
            case AUDIO -> props.getMaxAudioBytes();
        };
    }

    private static String label(Kind kind) {
        return switch (kind) {
            case IMAGE -> "photo";
            case VIDEO -> "video";
            case AUDIO -> "recording";
        };
    }

    /**
     * Read just the signature. Spring spools multipart parts to disk, so opening the
     * stream a second time to upload the body is safe and costs one file handle —
     * far cheaper than buffering the whole part to sniff four bytes.
     */
    private static byte[] readHeader(MultipartFile file) {
        byte[] header = new byte[SNIFF_LENGTH];
        int read = 0;
        try (InputStream in = file.getInputStream()) {
            while (read < SNIFF_LENGTH) {
                int n = in.read(header, read, SNIFF_LENGTH - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
        } catch (IOException e) {
            throw new MediaStorageException("Could not read the uploaded file.", e);
        }
        if (read == 0) {
            throw new MediaValidationException("The uploaded file is empty.");
        }
        return read == SNIFF_LENGTH ? header : Arrays.copyOf(header, read);
    }

    /**
     * @param declared the client's Content-Type. Used ONLY to pick audio-vs-video
     *                 inside the MPEG-4 and Matroska containers — see the class
     *                 comment. Never used to accept a format the bytes deny.
     * @return the detected type, or {@code null} if the bytes match nothing we allow
     */
    private static FileType detectType(byte[] header, String declared) {
        if (header.length < 4) {
            return null;
        }
        boolean saysAudio = declared != null && declared.toLowerCase(Locale.ROOT).startsWith("audio/");

        if (startsWith(header, JPEG_MAGIC)) {
            return FileType.JPEG;
        }
        if (startsWith(header, PNG_MAGIC)) {
            return FileType.PNG;
        }
        if (startsWith(header, MATROSKA_MAGIC)) {
            // Matroska and WebM share a container signature, and it carries audio or
            // video. Everything a phone or browser hands us is WebM; a true .mkv
            // mislabelled .webm costs nothing, both play from the same URL.
            return saysAudio ? FileType.WEBA : FileType.WEBM;
        }
        if (startsWith(header, ID3_MAGIC) || isMpegAudioFrame(header)) {
            return FileType.MP3;
        }
        if (startsWith(header, OGG_MAGIC)) {
            return FileType.OGG;
        }
        if (header.length < SNIFF_LENGTH) {
            return null;
        }
        // RIFF is a container too: "RIFF" <4-byte length> then the form type.
        if (startsWith(header, RIFF_MAGIC)) {
            if (Arrays.equals(header, 8, 12, WEBP_MAGIC, 0, 4)) {
                return FileType.WEBP;
            }
            if (Arrays.equals(header, 8, 12, WAVE_MAGIC, 0, 4)) {
                return FileType.WAV;
            }
            return null;
        }
        // ISO base media (MP4/MOV/3GP/M4A): <4-byte box size> "ftyp" <4-byte brand>.
        // The brand separates QuickTime ("qt  ", what an iPhone records) from every
        // MP4 flavour Android and the pickers produce (isom/iso2/mp41/mp42/avc1/3gp…).
        // "M4A " is unambiguous audio; every other brand needs the caller's hint,
        // because an expo-av voice note is an AAC track in a plain MP4 container.
        if (Arrays.equals(header, 4, 8, FTYP_MAGIC, 0, 4)) {
            String brand = new String(header, 8, 4, StandardCharsets.US_ASCII);
            if (brand.startsWith("M4A") || brand.startsWith("M4B")) {
                return FileType.M4A;
            }
            if (brand.startsWith("qt")) {
                return FileType.MOV;
            }
            return saysAudio ? FileType.M4A : FileType.MP4;
        }
        return null;
    }

    /**
     * A raw MPEG audio frame: 11 set sync bits, then a version/layer pair that is not
     * one of the reserved encodings. Checking the reserved values matters — without
     * it any file starting {@code FF Ex} is claimed as an MP3.
     */
    private static boolean isMpegAudioFrame(byte[] header) {
        if (header.length < 2 || (header[0] & 0xFF) != 0xFF || (header[1] & 0xE0) != 0xE0) {
            return false;
        }
        int version = (header[1] >> 3) & 0x03;
        int layer = (header[1] >> 1) & 0x03;
        return version != 0x01 && layer != 0x00;
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        return bytes.length >= prefix.length
                && Arrays.equals(bytes, 0, prefix.length, prefix, 0, prefix.length);
    }
}
