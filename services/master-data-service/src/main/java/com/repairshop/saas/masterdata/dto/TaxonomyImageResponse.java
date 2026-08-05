package com.repairshop.saas.masterdata.dto;

import java.util.UUID;

/**
 * Result of uploading a category tile or brand logo.
 *
 * {@code imageUrl} is composed from {@code imageKey} at response time and never
 * stored, so the CDN hostname lives in one place ({@code app.media.public-base-url}).
 * The key is returned too, so the admin can show where the file actually landed
 * instead of the old "stored as: inline (data URI)".
 *
 * @param id                category or brand id
 * @param name              name as saved
 * @param imageKey          e.g. {@code master/categories/audio-device-1f0ab993.jpg}
 * @param imageUrl          e.g. {@code https://media.ggfix.in/master/categories/audio-device-1f0ab993.jpg}
 * @param imageOriginalName filename as uploaded
 * @param imageContentType  MIME type proven by the file's bytes
 * @param imageSizeBytes    stored object size
 */
public record TaxonomyImageResponse(
        UUID id,
        String name,
        String imageKey,
        String imageUrl,
        String imageOriginalName,
        String imageContentType,
        Long imageSizeBytes) {
}
