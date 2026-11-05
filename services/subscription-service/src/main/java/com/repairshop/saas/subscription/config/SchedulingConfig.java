package com.repairshop.saas.subscription.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the invoice recovery sweep ({@code InvoiceIssuer#sweep}). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
