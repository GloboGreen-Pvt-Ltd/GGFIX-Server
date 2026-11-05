package com.repairshop.saas.masterdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Cloudinary credentials holder. No SDK dependency — MediaController posts
 * directly to Cloudinary's upload API via {@link RestTemplate}.
 *
 * If any of cloud-name / api-key / api-secret is blank, {@link #isConfigured()}
 * returns false and MediaController falls back to base64 data URIs.
 */
@Configuration
public class CloudinaryConfig {

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;

    public CloudinaryConfig(@Value("${app.cloudinary.cloud-name:}") String cloudName,
                            @Value("${app.cloudinary.api-key:}") String apiKey,
                            @Value("${app.cloudinary.api-secret:}") String apiSecret,
                            @Value("${app.cloudinary.folder:ggfix/master}") String folder) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = folder;
    }

    public boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    public String getCloudName() { return cloudName; }
    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getFolder() { return folder; }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
