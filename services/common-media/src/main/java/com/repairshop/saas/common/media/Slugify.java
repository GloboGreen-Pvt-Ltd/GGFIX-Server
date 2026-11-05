package com.repairshop.saas.common.media;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Turns catalogue names into URL-safe S3 path segments.
 *
 * <pre>
 *   Mobile     -> mobile
 *   Vivo       -> vivo
 *   Y Series   -> y-series
 *   Vivo Y20   -> vivo-y20
 *   Galaxy  S23 Ultra (5G) -> galaxy-s23-ultra-5g
 * </pre>
 *
 * S3 keys are byte strings, so almost anything is technically legal — but a key
 * containing a space, a {@code +} or a {@code #} needs escaping to survive a URL,
 * and the escaping differs between the browser, CloudFront and the SDK. Reducing
 * to {@code [a-z0-9-]} sidesteps that entirely.
 *
 * Accents are decomposed and stripped rather than dropped, so {@code Poco Ç} maps
 * to {@code poco-c} instead of {@code poco}.
 */
public final class Slugify {

    /** Guards against a pathological name blowing out the 1024-byte key limit. */
    private static final int MAX_SEGMENT_LENGTH = 120;

    private Slugify() {
    }

    /**
     * @return the slug, or {@code null} when the input has no usable characters
     *         (empty, blank, or punctuation only). Callers must treat null as a
     *         validation failure rather than substituting a placeholder — a
     *         silent fallback would collapse two different models onto one key.
     */
    public static String slugify(String raw) {
        if (raw == null) {
            return null;
        }
        // NFD splits "é" into "e" + combining accent so the accent can be removed
        // on its own; without this the whole character is stripped instead.
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        if (slug.isEmpty()) {
            return null;
        }
        if (slug.length() > MAX_SEGMENT_LENGTH) {
            slug = slug.substring(0, MAX_SEGMENT_LENGTH).replaceAll("-+$", "");
        }
        return slug;
    }

    /**
     * Slugify or fail loudly, naming the offending field.
     *
     * @param field label used in the error message, e.g. "category"
     */
    public static String requireSlug(String raw, String field) {
        String slug = slugify(raw);
        if (slug == null) {
            throw new MediaValidationException(
                    "Cannot build a media path: " + field + " ('" + raw + "') has no URL-safe characters.");
        }
        return slug;
    }
}
