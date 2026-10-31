package com.repairshop.saas.common.notify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live check of the Firebase credentials — only runs when credentials are
 * configured in the environment (GOOGLE_APPLICATION_CREDENTIALS locally, or
 * FIREBASE_CREDENTIALS_SECRET_ID against Secrets Manager); skipped otherwise,
 * so CI and other developers are unaffected. Sends nothing to any device: FCM
 * validates a dry-run message to an unused topic. Prints no credential content.
 */
class FcmCredentialsCheckTest {

    static boolean credentialsConfigured() {
        return System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null
                || System.getenv("FIREBASE_CREDENTIALS_SECRET_ID") != null;
    }

    @Test
    @EnabledIf("credentialsConfigured")
    void adminSdkAuthenticatesAgainstTheNearGFixProject() throws Exception {
        FcmSender fcm = new FcmSender("true",
                System.getenv().getOrDefault("FIREBASE_CREDENTIALS_SECRET_ID", ""),
                System.getenv().getOrDefault("FIREBASE_CREDENTIALS_SECRET_REGION", "ap-south-1"));

        assertTrue(fcm.isReady(), "Firebase Admin SDK did not initialise from " + fcm.credentialSource());
        assertEquals("neargfix", fcm.projectId());

        String id = fcm.dryRunCheck();
        assertTrue(id.startsWith("projects/neargfix/messages/"), "unexpected dry-run id: " + id);
    }
}
