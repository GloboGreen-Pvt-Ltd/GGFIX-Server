package com.repairshop.saas.masterdata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * The component scan is widened on purpose. @SpringBootApplication only scans its own
 * package downwards, and the shared S3 media beans live in
 * com.repairshop.saas.common.media — outside this tree. Without listing it,
 * S3StorageService and MediaProperties are never registered and uploads fail at
 * startup with a missing-bean error that looks nothing like the real cause.
 */
@SpringBootApplication
@ComponentScan({ "com.repairshop.saas.masterdata", "com.repairshop.saas.common.media" })
public class MasterDataServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MasterDataServiceApplication.class, args);
    }
}
