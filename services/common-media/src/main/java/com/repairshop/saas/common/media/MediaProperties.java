package com.repairshop.saas.common.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * S3 and CDN settings for media.ggfix.in.
 *
 * Bound from {@code app.media.*}. Credentials are deliberately absent: the SDK's
 * default provider chain resolves them from the instance role on EC2 and from the
 * environment locally, so no access key is ever written into a config file.
 */
@Component
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    /** Bucket behind the CloudFront distribution, e.g. {@code ggfix-media-1762}. */
    private String bucket = "";

    /** Bucket region. The media bucket is ap-south-1, unlike the us-east-1 CDN config. */
    private String region = "ap-south-1";

    /**
     * Public origin objects are served from, without a trailing slash.
     * {@code https://media.ggfix.in} in every environment that has the CNAME; point
     * it at the distribution domain if the alias is not attached yet.
     */
    private String publicBaseUrl = "https://media.ggfix.in";

    /** Per-image ceiling in bytes. Spring's multipart limit is the outer guard. */
    private long maxImageBytes = 5L * 1024 * 1024;

    /**
     * Per-document ceiling. Higher than images on purpose: a scanned Aadhaar or a
     * multi-page GST certificate is routinely larger than a product photo, and
     * rejecting a customer's real document is worse than storing a few extra MB.
     */
    private long maxDocumentBytes = 10L * 1024 * 1024;

    /**
     * Cache-Control written onto every uploaded object. Filenames carry a unique
     * suffix and are never rewritten in place, so objects are safe to cache
     * immutably and effectively forever — a replacement produces a NEW key, which
     * is what makes an invalidation unnecessary.
     */
    private String cacheControl = "public,max-age=31536000,immutable";

    /** @return true when a bucket is configured and S3 uploads can be attempted */
    public boolean isConfigured() {
        return bucket != null && !bucket.isBlank();
    }

    /** Absolute public URL for an object key. */
    public String publicUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String base = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        return base + "/" + key.replaceAll("^/+", "");
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public long getMaxDocumentBytes() {
        return maxDocumentBytes;
    }

    public void setMaxDocumentBytes(long maxDocumentBytes) {
        this.maxDocumentBytes = maxDocumentBytes;
    }

    public String getCacheControl() {
        return cacheControl;
    }

    public void setCacheControl(String cacheControl) {
        this.cacheControl = cacheControl;
    }
}
