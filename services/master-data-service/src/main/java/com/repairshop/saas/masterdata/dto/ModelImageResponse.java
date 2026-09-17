package com.repairshop.saas.masterdata.dto;

import java.util.UUID;

/**
 * What the admin gets back after creating a model or replacing its image.
 *
 * {@code imageUrl} is composed from {@code imageKey} at response time, never stored,
 * so the CDN hostname lives in exactly one place ({@code app.media.public-base-url}).
 * Both keys are returned as well: the client shows the URL, but the keys are what
 * make the stored layout auditable from the admin UI.
 *
 * @param id                model id
 * @param name              model name as saved
 * @param slug              URL slug derived from the name
 * @param mediaFolderKey    e.g. {@code mobile/vivo/y-series/vivo-y20}
 * @param imageKey          e.g. {@code mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg}
 * @param imageUrl          e.g. {@code https://media.ggfix.in/mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg}
 * @param imageOriginalName filename as uploaded
 * @param imageContentType  MIME type proven by the file's bytes
 * @param imageSizeBytes    stored object size
 * @param previousImageUrl  what the row held before this upload, null on create
 * @param previousImageRemoved
 *        whether that older object was deleted from the bucket. False also covers
 *        "there was nothing of ours to delete" — a data URI, a Cloudinary link, or
 *        an image another row still uses. The admin says which of the two happened
 *        by looking at previousImageUrl, so a replacement that left bytes behind is
 *        visible rather than silently assumed.
 */
public record ModelImageResponse(
        UUID id,
        String name,
        String slug,
        String mediaFolderKey,
        String imageKey,
        String imageUrl,
        String imageOriginalName,
        String imageContentType,
        Long imageSizeBytes,
        String previousImageUrl,
        boolean previousImageRemoved) {
}
