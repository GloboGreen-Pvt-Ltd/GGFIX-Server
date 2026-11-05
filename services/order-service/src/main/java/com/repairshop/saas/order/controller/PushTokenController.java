package com.repairshop.saas.order.controller;

import com.repairshop.saas.common.notify.PushApp;
import com.repairshop.saas.common.notify.PushService;
import com.repairshop.saas.order.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Push registration for all three NearGFix apps (Customer, Partner, Employee).
 *
 *   POST /push-tokens         { token, platform, app }  → 204  (after sign-in)
 *   POST /push-tokens/remove  { token }                 → 204  (on sign-out)
 *
 * {@code token} is a native FCM registration token (Android) or an Expo push
 * token. {@code app} is CUSTOMER | PARTNER | EMPLOYEE and must match the
 * caller's role; when omitted it is inferred from the role. Partner devices
 * are bound to the JWT's shop, the others to the JWT's user. The Partner app's
 * existing POST /shop/push-tokens keeps working and lands in the same table.
 */
@RestController
@RequestMapping("/push-tokens")
@RequiredArgsConstructor
public class PushTokenController {

    private final PushService push;

    @PostMapping
    public ResponseEntity<Void> register(HttpServletRequest req, @RequestBody PushTokenRequest body) {
        Collection<String> roles = roles(req);
        PushApp app = body.getApp() != null ? PushApp.parse(body.getApp()) : PushApp.inferFrom(roles);
        if (app == null) throw new IllegalArgumentException("Unknown app — use CUSTOMER, PARTNER or EMPLOYEE");
        if (!app.allows(roles)) throw new ForbiddenException("This account cannot register for the " + app + " app");
        UUID userId = uuid(req.getAttribute("userId"));
        UUID shopId = uuid(req.getAttribute("shopId"));
        if (app == PushApp.PARTNER && shopId == null) throw new ForbiddenException("Missing shopId — sign in as a shop owner");
        Object scope = req.getAttribute("loginScope");
        push.register(app, body.getToken(), userId, app == PushApp.CUSTOMER ? null : shopId,
                scope != null ? scope.toString() : null, body.getPlatform());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/remove")
    public ResponseEntity<Void> remove(@RequestBody PushTokenRequest body) {
        push.unregister(body.getToken());
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> roles(HttpServletRequest req) {
        Object r = req.getAttribute("roles");
        return r instanceof Collection<?> c ? (Collection<String>) c : List.of();
    }

    private static UUID uuid(Object v) {
        if (v == null) return null;
        try { return UUID.fromString(v.toString()); } catch (IllegalArgumentException e) { return null; }
    }

    @Data
    public static class PushTokenRequest {
        private String token;
        private String platform; // android | ios
        private String app;      // CUSTOMER | PARTNER | EMPLOYEE (optional)
    }
}
