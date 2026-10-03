package com.repairshop.saas.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// common.devicespecs carries the per-category device specification rules shared
// with ticket-service; it is a plain jar with no auto-configuration, so it has
// to be named here to be found.
@SpringBootApplication(scanBasePackages = {
        "com.repairshop.saas.marketplace",
        "com.repairshop.saas.common.devicespecs"
})
public class MarketplaceServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketplaceServiceApplication.class, args);
    }
}
