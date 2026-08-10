package com.repairshop.saas.common.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Optional;

/**
 * Puts and deletes objects in the media bucket.
 *
 * No folder is ever created: S3 has no directories. Writing
 * {@code mobile/vivo/y-series/vivo-y20/main-a82f5c1.jpg} is a single PutObject, and
 * the console renders the nesting from the key's slashes. Creating "folder" objects
 * would leave zero-byte keys that CloudFront would happily serve as empty files.
 */
@Service
public class S3StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    private final S3Client s3;
    private final MediaProperties props;

    /**
     * @param s3 absent when no bucket is configured — see
     *           {@link MediaConfig#s3Client}. Uploads then fail with a clear
     *           message instead of the service refusing to start, which keeps the
     *           other master-data endpoints usable in a local dev setup with no
     *           AWS credentials.
     */
    public S3StorageService(Optional<S3Client> s3, MediaProperties props) {
        this.s3 = s3.orElse(null);
        this.props = props;
    }

    public boolean isEnabled() {
        return s3 != null && props.isConfigured();
    }

    /**
     * Upload one object under an exact key.
     *
     * @return the public URL the object is now reachable at
     * @throws MediaStorageException if S3 is unconfigured or the put fails
     */
    public String put(String key, byte[] bytes, String contentType) {
        requireEnabled();
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    .cacheControl(props.getCacheControl())
                    .build();
            s3.putObject(request, RequestBody.fromBytes(bytes));
            log.info("Uploaded s3://{}/{} ({} bytes, {})", props.getBucket(), key, bytes.length, contentType);
            return props.publicUrl(key);
        } catch (S3Exception e) {
            // awsErrorDetails carries the real cause (AccessDenied, NoSuchBucket);
            // the bare SDK message alone is close to useless in a log.
            String detail = e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage();
            throw new MediaStorageException("S3 upload failed for key '" + key + "': " + detail, e);
        } catch (RuntimeException e) {
            throw new MediaStorageException("S3 upload failed for key '" + key + "'.", e);
        }
    }

    /**
     * Delete one object, ignoring the case where it is already gone.
     *
     * @throws MediaStorageException if S3 is unconfigured or the delete fails
     */
    public void delete(String key) {
        requireEnabled();
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(props.getBucket())
                    .key(key)
                    .build());
            log.info("Deleted s3://{}/{}", props.getBucket(), key);
        } catch (S3Exception e) {
            String detail = e.awsErrorDetails() == null ? e.getMessage() : e.awsErrorDetails().errorMessage();
            throw new MediaStorageException("S3 delete failed for key '" + key + "': " + detail, e);
        } catch (RuntimeException e) {
            throw new MediaStorageException("S3 delete failed for key '" + key + "'.", e);
        }
    }

    /**
     * Best-effort delete for compensating paths — rolling back a failed transaction,
     * or removing the image a replacement superseded.
     *
     * Never throws. In both cases the caller's own outcome is already decided, and
     * letting a cleanup failure surface would turn a successful save into an error
     * response, or mask the original exception being propagated. A leaked object is
     * logged at WARN and is recoverable by prefix sweep; a lost update is not.
     *
     * @return true when the delete was issued and accepted. Callers on the rollback
     *         path ignore this; the replacement path reports it to the admin, which
     *         is the difference between "the old image is gone" and "the old image
     *         is still sitting in the bucket".
     */
    public boolean deleteQuietly(String key) {
        if (key == null || key.isBlank() || !isEnabled()) {
            return false;
        }
        try {
            delete(key);
            return true;
        } catch (RuntimeException e) {
            log.warn("Orphaned S3 object s3://{}/{} — delete failed and was swallowed: {}",
                    props.getBucket(), key, e.getMessage());
            return false;
        }
    }

    /**
     * Remove the object a replacement superseded, given the URL the record used to
     * hold and the key it holds now.
     *
     * Call this only AFTER the new URL is committed. Two things have to be true for
     * anything to be deleted, and both are checked here rather than at each call
     * site: the old URL must resolve to a key in our own bucket
     * ({@link MediaProperties#keyForPublicUrl}), and it must not be the key just
     * written — re-uploading the identical object under the same key would otherwise
     * delete the live image. Whether the key belongs to THIS kind of record is the
     * caller's check, since only it knows the expected layout.
     *
     * @return true when an object was actually removed
     */
    public boolean deleteSupersededQuietly(String previousUrl, String currentKey) {
        String previousKey = props.keyForPublicUrl(previousUrl);
        if (previousKey == null || previousKey.equals(currentKey)) {
            return false;
        }
        return deleteQuietly(previousKey);
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new MediaStorageException(
                    "S3 media storage is not configured. Set app.media.bucket (MEDIA_S3_BUCKET) "
                            + "and provide AWS credentials.");
        }
    }
}
