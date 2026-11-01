package com.repairshop.saas.ticket.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * Receipt images in the media bucket under wa-receipts/{shopId}/{random}.png:
 * never cached, handed to BotBee as a short-lived presigned URL, and deleted by
 * the worker after app.whatsapp.receipt-retention-hours. Credentials come from
 * the SDK default chain (EC2 instance role), never from config.
 */
@Component
public class ReceiptMediaStore {

    private static final Logger log = LoggerFactory.getLogger(ReceiptMediaStore.class);

    private final WhatsAppProperties props;
    private volatile S3Client s3;
    private volatile S3Presigner presigner;

    public ReceiptMediaStore(WhatsAppProperties props) {
        this.props = props;
    }

    public boolean isEnabled() {
        return props.getReceiptBucket() != null && !props.getReceiptBucket().isBlank();
    }

    /** Uploads the PNG and returns its object key. */
    public String put(UUID shopId, byte[] png) {
        String key = "wa-receipts/" + shopId + "/" + UUID.randomUUID() + ".png";
        client().putObject(PutObjectRequest.builder()
                        .bucket(props.getReceiptBucket())
                        .key(key)
                        .contentType("image/png")
                        .contentLength((long) png.length)
                        .cacheControl("private, no-store")
                        .build(),
                RequestBody.fromBytes(png));
        return key;
    }

    /** URL BotBee/Meta fetch the header image from. */
    public String url(String key) {
        if (props.isPublicMediaUrls()) {
            return props.getPublicBaseUrl().replaceAll("/+$", "") + "/" + key;
        }
        return presigner().presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(Math.max(5, props.getMediaUrlMinutes())))
                        .getObjectRequest(GetObjectRequest.builder().bucket(props.getReceiptBucket()).key(key).build())
                        .build())
                .url().toString();
    }

    public boolean deleteQuietly(String key) {
        try {
            client().deleteObject(DeleteObjectRequest.builder().bucket(props.getReceiptBucket()).key(key).build());
            return true;
        } catch (RuntimeException e) {
            log.warn("WhatsApp receipt delete failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    private S3Client client() {
        if (s3 == null) {
            synchronized (this) {
                if (s3 == null) {
                    s3 = S3Client.builder()
                            .region(Region.of(props.getReceiptRegion()))
                            .httpClientBuilder(UrlConnectionHttpClient.builder())
                            .build();
                }
            }
        }
        return s3;
    }

    private S3Presigner presigner() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    presigner = S3Presigner.builder().region(Region.of(props.getReceiptRegion())).build();
                }
            }
        }
        return presigner;
    }
}
