package com.repairshop.saas.order.controller;

import com.repairshop.saas.common.notify.ShopPushService;
import com.repairshop.saas.order.exception.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Partner app push registration. Next to ShopNotificationController because a
 * push is the same feed delivered to the phone: same callers (SHOP_OWNER and
 * SHOP_LOGIN tokens), same shopId resolution from the JWT.
 *
 *   POST /shop/push-tokens         { token, platform }  → 204  (after sign-in / shop switch)
 *   POST /shop/push-tokens/remove  { token }            → 204  (on sign-out)
 *
 * The token is a native FCM token or an Expo push token; registering it again from another shop or
 * account moves it there.
 */
@RestController
@RequestMapping("/shop/push-tokens")
@RequiredArgsConstructor
public class ShopPushTokenController {

    private final ShopPushService push;

    @PostMapping
    public ResponseEntity<Void> register(HttpServletRequest req, @RequestBody PushTokenRequest body) {
        UUID shopId = shopCallerId(req);
        Object user = req.getAttribute("userId");
        Object scope = req.getAttribute("loginScope");
        push.register(body.getToken(),
                user != null ? UUID.fromString(user.toString()) : null,
                shopId,
                scope != null ? scope.toString() : null,
                body.getPlatform());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/remove")
    public ResponseEntity<Void> remove(HttpServletRequest req, @RequestBody PushTokenRequest body) {
        shopCallerId(req);
        push.unregister(body.getToken());
        return ResponseEntity.noContent().build();
    }

    private UUID shopCallerId(HttpServletRequest req) {
        Object s = req.getAttribute("shopId");
        if (s == null) throw new ForbiddenException("Missing shopId — sign in as a shop owner");
        return UUID.fromString(s.toString());
    }

    @Data
    public static class PushTokenRequest {
        private String token;
        private String platform; // android | ios
    }
}
