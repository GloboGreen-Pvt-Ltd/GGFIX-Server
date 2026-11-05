package com.repairshop.saas.common.subscription;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when an action would take an account past its plan allowance, or when
 * the subscription behind it has lapsed.
 *
 * <p>Carries the whole {@link LimitCheck} rather than just a message so the
 * client can render the counter and the upgrade prompt from the rejection
 * itself — the app never has to make a second call to find out what the limit
 * was. Consuming services map this to <b>409 Conflict</b> in their
 * {@code @RestControllerAdvice} via {@link #toBody()}.
 *
 * <p>409 rather than 403 deliberately: the caller is authenticated and
 * authorised, and the request will succeed once the conflicting state (too many
 * employees, a lapsed plan) is resolved. Clients already log the user out on a
 * 401 from auth-service, and overloading 403 here risks that reflex firing on
 * what is really a billing prompt.
 */
public class SubscriptionLimitExceededException extends RuntimeException {

    private final LimitCheck check;

    public SubscriptionLimitExceededException(LimitCheck check) {
        super(check.message());
        this.check = check;
    }

    public LimitCheck getCheck() {
        return check;
    }

    /** Flat JSON body: the LimitCheck fields plus `code`/`message` for clients
     *  that only read the standard error envelope. */
    public Map<String, Object> toBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", check.reason());
        body.put("error", check.reason());
        body.put("message", check.message());
        body.put("allowed", check.allowed());
        body.put("currentUsage", check.currentUsage());
        body.put("limit", check.limit());
        body.put("remaining", check.remaining());
        body.put("plan", check.plan());
        body.put("planName", check.planName());
        body.put("feature", check.feature());
        body.put("expired", check.expired());
        body.put("status", check.status());
        return body;
    }
}
