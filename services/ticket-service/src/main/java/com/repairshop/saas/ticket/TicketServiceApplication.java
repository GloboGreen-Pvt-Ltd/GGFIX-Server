package com.repairshop.saas.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// common.subscription carries the shared plan catalogue and limit engine, and
// common.devicespecs the per-category device specification rules; both are
// plain jars with no auto-configuration, so they have to be named here to be found.
@SpringBootApplication(scanBasePackages = {
        "com.repairshop.saas.ticket",
        "com.repairshop.saas.common.subscription",
        "com.repairshop.saas.common.devicespecs"
})
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
