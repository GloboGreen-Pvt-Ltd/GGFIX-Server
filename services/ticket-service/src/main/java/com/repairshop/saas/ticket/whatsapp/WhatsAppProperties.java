package com.repairshop.saas.ticket.whatsapp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * WhatsApp booking confirmations through BotBee Nexa (app.whatsapp.*, from the
 * BOTBEE_* environment). Sending is OFF unless enabled=true AND an API key is
 * set — keep it off until Meta approves the template. The key never lives in
 * application.yml or git.
 */
@Data
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {

    /** Master switch (BOTBEE_WHATSAPP_ENABLED). false = rows are recorded as SKIPPED, nothing is sent. */
    private boolean enabled = false;
    private String baseUrl = "https://nexa.botbee.io";
    private String apiKey = "";
    /** BotBee channel (WhatsApp number) id, when the workspace API needs one. */
    private String channelId = "";
    private String bookingTemplate = "neargfix_booking_confirmed";
    private String language = "en";
    private String defaultCountryCode = "91";
    private String timezone = "Asia/Kolkata";

    /** Wait after the first device so a multi-device booking goes out as ONE message. */
    private int gatherSeconds = 45;
    private int maxAttempts = 6;
    private int maxResends = 3;
    /** Per worker run (every 15 s): 5 keeps sends under BotBee's 20/min messaging limit. */
    private int batchSize = 5;
    /** Delivery status is polled (message/status) for this long after sending. */
    private int statusPollHours = 48;
    private int requestTimeoutSeconds = 20;

    /** Receipt images: private S3 objects, deleted after this many hours. */
    private int receiptRetentionHours = 24;
    /** Lifetime of the presigned receipt URL handed to BotBee. */
    private int mediaUrlMinutes = 60;
    /** true = hand BotBee the public media URL instead of a presigned one. */
    private boolean publicMediaUrls = false;
    private String receiptBucket = "";
    private String receiptRegion = "ap-south-1";
    private String publicBaseUrl = "https://media.ggfix.in";
    /** Used as the header image only when the receipt cannot be rendered/uploaded. */
    private String fallbackHeaderImageUrl = "";

    public boolean isSendingOn() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
