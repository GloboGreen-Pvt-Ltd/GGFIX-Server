package com.repairshop.saas.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The scan is widened because the shared S3 media beans live in
 * com.repairshop.saas.common.media, outside this package tree — @SpringBootApplication
 * alone would not register them and uploads would fail on a missing bean. The
 * subscription limit engine (com.repairshop.saas.common.subscription) is added
 * for the same reason.
 */
@SpringBootApplication
@ComponentScan({
        "com.repairshop.saas.auth",
        "com.repairshop.saas.common.media",
        "com.repairshop.saas.common.subscription"
})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
