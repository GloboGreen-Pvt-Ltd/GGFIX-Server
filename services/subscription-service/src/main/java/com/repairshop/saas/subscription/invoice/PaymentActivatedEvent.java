package com.repairshop.saas.subscription.invoice;

import java.util.UUID;

/**
 * Published inside {@code OrderLifecycleService.activatePaid} when a captured
 * payment activates a subscription; {@link InvoiceIssuer} acts on it only after
 * that transaction commits, off the request thread.
 */
public record PaymentActivatedEvent(UUID paymentId, UUID orderId) {}
