package com.repairshop.saas.common.notify;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.ImplFirebaseTrampolines;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sends to native FCM registration tokens with the Firebase Admin SDK.
 *
 * <h2>Credentials — never in the repo, the jar, the image or the .env</h2>
 * <ul>
 *   <li><b>Production:</b> {@code FIREBASE_CREDENTIALS_SECRET_ID} names an AWS
 *       Secrets Manager secret holding the service-account JSON; it is read
 *       with the EC2 instance role and kept in memory only.</li>
 *   <li><b>Local / workload identity federation:</b> otherwise Google
 *       Application Default Credentials — {@code GOOGLE_APPLICATION_CREDENTIALS}
 *       pointing at a key file outside the repo, or at a WIF
 *       {@code external_account} config.</li>
 * </ul>
 * Initialised lazily on the first send, so a missing or broken credential
 * never stops a service from booting: FCM sends are skipped (and logged) and a
 * new attempt is made at most every few minutes. Nothing about the
 * credential's content is ever logged.
 *
 * <p>Data is sent flat (string values): expo-notifications on Android copies
 * a direct-FCM message's data map into {@code notification.request.content.data}
 * as long as it has no JSON {@code body} key, which is why those keys are
 * dropped here.
 */
@Component
public class FcmSender {

    private static final Logger log = LoggerFactory.getLogger(FcmSender.class);

    /** Named app, so a service that ever initialises the default FirebaseApp is unaffected. */
    static final String APP_NAME = "neargfix-push";
    /** FCM's sendEach limit. */
    private static final int BATCH = 500;
    private static final long RETRY_MS = 5 * 60_000L;
    /** Reserved by FCM, or remapped by expo-notifications (title/body/message). */
    private static final Set<String> DROPPED_DATA_KEYS =
            Set.of("from", "notification", "message_type", "body", "title", "message");

    /** One outgoing push. */
    public record FcmMessage(String token, String title, String body, Map<String, ?> data) {}

    private final boolean enabled;
    private final String secretId;
    private final String secretRegion;

    private volatile FirebaseMessaging messaging;
    private volatile long retryAfter;

    public FcmSender(
            @Value("${push.fcm.enabled:${FCM_ENABLED:true}}") String enabled,
            @Value("${push.fcm.credentials-secret-id:${FIREBASE_CREDENTIALS_SECRET_ID:}}") String secretId,
            @Value("${push.fcm.credentials-secret-region:${FIREBASE_CREDENTIALS_SECRET_REGION:${AWS_REGION:ap-south-1}}}") String secretRegion) {
        this.enabled = !"false".equalsIgnoreCase(enabled == null ? "" : enabled.trim());
        this.secretId = secretId == null ? "" : secretId.trim();
        this.secretRegion = secretRegion == null || secretRegion.isBlank() ? "ap-south-1" : secretRegion.trim();
    }

    /** True once the Admin SDK is initialised (initialising it on first call). */
    public boolean isReady() {
        return messaging() != null;
    }

    /** Where credentials are read from — for logs and health output; never the content. */
    public String credentialSource() {
        if (!secretId.isEmpty()) return "AWS Secrets Manager (" + secretRegion + ")";
        return System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null
                ? "GOOGLE_APPLICATION_CREDENTIALS" : "Application Default Credentials";
    }

    /** Firebase project the credentials belong to, or null when not initialised. */
    public String projectId() {
        FirebaseMessaging m = messaging();
        if (m == null) return null;
        return ImplFirebaseTrampolines.getProjectId(FirebaseApp.getInstance(APP_NAME));
    }

    /**
     * Validates credentials end to end without delivering anything: a dry-run
     * send to an unused topic. Returns FCM's message id
     * ({@code projects/<project>/messages/...}).
     */
    public String dryRunCheck() throws FirebaseMessagingException {
        FirebaseMessaging m = messaging();
        if (m == null) throw new IllegalStateException("FCM is not configured (" + credentialSource() + ")");
        return m.send(Message.builder()
                .setTopic("neargfix-config-check")
                .setNotification(Notification.builder().setTitle("config check").build())
                .build(), true);
    }

    /**
     * Sends each message; returns the tokens FCM says will never work again
     * (uninstalled app, token from another Firebase project). Never throws.
     */
    public List<String> send(List<FcmMessage> messages) {
        List<String> dead = new ArrayList<>();
        if (messages == null || messages.isEmpty()) return dead;
        FirebaseMessaging m = messaging();
        if (m == null) {
            log.warn("FCM not configured — skipped {} push(es)", messages.size());
            return dead;
        }
        for (int i = 0; i < messages.size(); i += BATCH) {
            List<FcmMessage> chunk = messages.subList(i, Math.min(i + BATCH, messages.size()));
            try {
                BatchResponse res = m.sendEach(chunk.stream().map(FcmSender::toMessage).toList());
                List<SendResponse> responses = res.getResponses();
                for (int j = 0; j < responses.size() && j < chunk.size(); j++) {
                    SendResponse r = responses.get(j);
                    if (r.isSuccessful()) continue;
                    FirebaseMessagingException ex = r.getException();
                    if (isDeadToken(ex)) {
                        dead.add(chunk.get(j).token());
                    } else {
                        log.warn("FCM send error {}: {}", ex == null ? "?" : ex.getMessagingErrorCode(),
                                ex == null ? "" : clip(ex.getMessage(), 200));
                    }
                }
            } catch (Exception e) {
                log.warn("FCM batch failed: {}", clip(e.getMessage(), 300));
            }
        }
        return dead;
    }

    static boolean isDeadToken(FirebaseMessagingException ex) {
        if (ex == null) return false;
        MessagingErrorCode code = ex.getMessagingErrorCode();
        if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.SENDER_ID_MISMATCH) return true;
        // INVALID_ARGUMENT also covers payload mistakes; only a bad token is permanent.
        return code == MessagingErrorCode.INVALID_ARGUMENT
                && ex.getMessage() != null && ex.getMessage().toLowerCase().contains("registration token");
    }

    static Message toMessage(FcmMessage msg) {
        return Message.builder()
                .setToken(msg.token())
                .setNotification(Notification.builder()
                        .setTitle(msg.title() == null ? "" : msg.title())
                        .setBody(msg.body() == null ? "" : msg.body())
                        .build())
                .putAllData(stringData(msg.data()))
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("default")
                                .setSound("default")
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder().setSound("default").build())
                        .build())
                .build();
    }

    static Map<String, String> stringData(Map<String, ?> data) {
        Map<String, String> out = new LinkedHashMap<>();
        if (data == null) return out;
        data.forEach((k, v) -> {
            if (k == null || v == null || DROPPED_DATA_KEYS.contains(k)) return;
            if (k.startsWith("google.") || k.startsWith("gcm.")) return;
            out.put(k, String.valueOf(v));
        });
        return out;
    }

    private FirebaseMessaging messaging() {
        FirebaseMessaging m = messaging;
        if (m != null || !enabled) return m;
        synchronized (this) {
            if (messaging != null) return messaging;
            if (System.currentTimeMillis() < retryAfter) return null;
            try {
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(a -> APP_NAME.equals(a.getName()))
                        .findFirst()
                        .orElseGet(() -> FirebaseApp.initializeApp(options(), APP_NAME));
                messaging = FirebaseMessaging.getInstance(app);
                log.info("FCM ready — project {}, credentials from {}",
                        ImplFirebaseTrampolines.getProjectId(app), credentialSource());
            } catch (Exception e) {
                retryAfter = System.currentTimeMillis() + RETRY_MS;
                // Class + message only: credential parsing errors never echo the key.
                log.warn("FCM not configured (credentials from {}): {}: {}",
                        credentialSource(), e.getClass().getSimpleName(), clip(e.getMessage(), 200));
            }
            return messaging;
        }
    }

    private FirebaseOptions options() {
        try {
            GoogleCredentials credentials;
            if (!secretId.isEmpty()) {
                try (InputStream in = new ByteArrayInputStream(readSecret())) {
                    credentials = GoogleCredentials.fromStream(in);
                }
            } else {
                credentials = GoogleCredentials.getApplicationDefault();
            }
            // FirebaseOptions scopes the credentials itself; the project id comes from them.
            return FirebaseOptions.builder().setCredentials(credentials).build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    private byte[] readSecret() {
        try (SecretsManagerClient sm = SecretsManagerClient.builder()
                .region(Region.of(secretRegion))
                .httpClient(UrlConnectionHttpClient.create())
                .build()) {
            GetSecretValueResponse res = sm.getSecretValue(b -> b.secretId(secretId));
            if (res.secretString() != null) return res.secretString().getBytes(StandardCharsets.UTF_8);
            SdkBytes binary = res.secretBinary();
            if (binary != null) return binary.asByteArray();
            throw new IllegalStateException("Secret has no value");
        }
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
