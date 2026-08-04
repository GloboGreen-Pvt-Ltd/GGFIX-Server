package com.repairshop.saas.masterdata.media;

/**
 * The caller's input cannot produce a valid media object — unsupported MIME type,
 * oversized file, a name with no URL-safe characters, or a brand/series that does
 * not belong to the parent it was submitted under.
 *
 * Maps to HTTP 400 in {@link MediaExceptionHandler}. Distinct from
 * {@link MediaStorageException}, which means the request was fine but S3 failed.
 */
public class MediaValidationException extends RuntimeException {

    public MediaValidationException(String message) {
        super(message);
    }
}
