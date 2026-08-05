package com.repairshop.saas.common.media;

/**
 * S3 refused or failed the operation — credentials, bucket policy, network, or an
 * unexpected SDK error. The request itself was well-formed.
 *
 * Maps to HTTP 502 in {@link MediaExceptionHandler}: the fault is in a service we
 * depend on, not in the caller's request, and a 500 would wrongly suggest a bug in
 * master-data-service itself.
 */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public MediaStorageException(String message) {
        super(message);
    }
}
