package com.repairshop.saas.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// common.subscription carries the shared plan catalogue and limit engine; it is
// a plain jar with no auto-configuration, so it has to be named here to be found.
@SpringBootApplication(scanBasePackages = {
        "com.repairshop.saas.ticket",
        "com.repairshop.saas.common.subscription"
})
public class TicketServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketServiceApplication.class, args);
    }
}
