package com.repairshop.saas.common.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Builds the S3 client, or none at all when no bucket is configured.
 *
 * Returning null from the bean method (rather than failing startup) keeps every
 * other master-data endpoint working on a developer machine with no AWS access;
 * {@link S3StorageService} then rejects uploads with an actionable message. The
 * service is injected as {@code Optional<S3Client>} for exactly this reason.
 */
@Configuration
public class MediaConfig {

    private static final Logger log = LoggerFactory.getLogger(MediaConfig.class);

    @Bean
    public S3Client s3Client(MediaProperties props) {
        if (!props.isConfigured()) {
            log.warn("app.media.bucket is not set — S3 media uploads are disabled. "
                    + "Set MEDIA_S3_BUCKET to enable them.");
            return null;
        }
        log.info("S3 media storage: bucket={} region={} public={}",
                props.getBucket(), props.getRegion(), props.getPublicBaseUrl());

        return S3Client.builder()
                .region(Region.of(props.getRegion()))
                // The JDK's HttpURLConnection client. Apache and Netty are excluded
                // in the pom to keep the fat jar small on the shared t3.micro; this
                // is the supported synchronous client for that setup.
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                // No credentialsProvider on purpose: the builder already falls back
                // to the default provider chain — instance role on EC2, environment
                // or ~/.aws locally. Never a key in application.yml.
                .build();
    }
}
