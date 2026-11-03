package com.repairshop.saas.common.notify;

import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Push notifications to the Partner (shop) app — the shop-addressed face of
 * {@link PushService}. A push to a shop goes to every device registered for
 * it: the owner's phone and any SHOP-login counter devices, over FCM or Expo
 * depending on the token each device registered.
 */
@Service
public class ShopPushService {

    private final PushService push;

    public ShopPushService(PushService push) {
        this.push = push;
    }

    public static boolean isExpoToken(String token) {
        return PushTokenRegistry.providerOf(token) == PushTokenRegistry.Provider.EXPO;
    }

    // ── Registration ────────────────────────────────────────────────────────

    /**
     * Upsert by token. A token identifies one app install, so when the device
     * signs in to another account or shop the row moves with it — the previous
     * shop must stop receiving that phone's alerts.
     */
    public void register(String token, UUID userId, UUID shopId, String loginType, String platform) {
        push.register(PushApp.PARTNER, token, userId, shopId, loginType, platform);
    }

    /** Sign-out: this device stops receiving the shop's alerts. Unknown tokens are a no-op. */
    public void unregister(String token) {
        push.unregister(token);
    }

    // ── Sending ─────────────────────────────────────────────────────────────

    public void pushToShop(UUID shopId, String title, String body, Map<String, Object> data) {
        if (shopId == null) return;
        pushToShops(List.of(shopId), title, body, data);
    }

    public void pushToShops(Collection<UUID> shopIds, String title, String body, Map<String, Object> data) {
        if (shopIds == null || shopIds.isEmpty()) return;
        Map<UUID, Map<String, Object>> byShop = new LinkedHashMap<>();
        for (UUID id : shopIds) if (id != null) byShop.put(id, data);
        pushEach(title, body, byShop);
    }

    /**
     * Same title/body to several shops, with per-shop data — used when each shop
     * has its own shop_notifications row and the tap has to open that row.
     */
    public void pushEach(String title, String body, Map<UUID, Map<String, Object>> dataByShop) {
        push.pushToShops(dataByShop, title, body);
    }

    /** See {@link PushService#afterCommit(Runnable)}. */
    public void afterCommit(Runnable task) {
        push.afterCommit(task);
    }

    /** See {@link PushService#quiet()}. Use with try-with-resources. */
    public Quiet quiet() {
        PushService.Quiet q = push.quiet();
        return q::close;
    }

    /** {@link AutoCloseable} without the checked exception. */
    public interface Quiet extends AutoCloseable {
        @Override void close();
    }
}
