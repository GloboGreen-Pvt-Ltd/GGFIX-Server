package com.repairshop.saas.subscription;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// common.subscription carries the shared plan catalogue and limit engine; it is
// a plain jar with no auto-configuration, so it has to be named here to be found.
@SpringBootApplication(scanBasePackages = {
        "com.repairshop.saas.subscription",
        "com.repairshop.saas.common.subscription"
})
public class SubscriptionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SubscriptionServiceApplication.class, args);
    }
}
