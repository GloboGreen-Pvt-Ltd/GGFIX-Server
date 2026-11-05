package com.repairshop.saas.common.notify;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@code push_device_tokens}: one row per app install, for all three NearGFix
 * apps. Plain JDBC (see the module pom). A token identifies the install, not
 * the person, so registering it again moves the row to the new account / shop.
 */
@Component
public class PushTokenRegistry {

    /** Keeps the IN (...) list well under the JDBC parameter limit. */
    private static final int LOOKUP_CHUNK = 500;
    private static final Pattern EXPO_TOKEN = Pattern.compile("^Expo(nent)?PushToken\\[[^\\]\\s]+\\]$");
    /** FCM registration tokens: long, URL-safe base64 plus ':' — never whitespace. */
    private static final Pattern FCM_TOKEN = Pattern.compile("^[A-Za-z0-9_:\\-]{100,4096}$");

    /** Which service delivers to a token. */
    public enum Provider { FCM, EXPO }

    /** A registered device. */
    public record Device(String token, Provider provider) {}

    private final JdbcTemplate jdbc;

    public PushTokenRegistry(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** FCM for a native Android/Firebase token, EXPO for an Expo push token, null when it is neither. */
    public static Provider providerOf(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (EXPO_TOKEN.matcher(t).matches()) return Provider.EXPO;
        if (FCM_TOKEN.matcher(t).matches()) return Provider.FCM;
        return null;
    }

    public void register(PushApp app, String token, UUID userId, UUID shopId, String loginType, String platform) {
        if (app == null) throw new IllegalArgumentException("app is required");
        Provider provider = providerOf(token);
        if (provider == null) throw new IllegalArgumentException("Not an FCM or Expo push token");
        if (app == PushApp.PARTNER && shopId == null) throw new IllegalArgumentException("shopId is required");
        if (app != PushApp.PARTNER && userId == null) throw new IllegalArgumentException("userId is required");
        jdbc.update(
                "INSERT INTO push_device_tokens (token, app, provider, user_id, shop_id, login_type, platform) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (token) DO UPDATE SET app = EXCLUDED.app, provider = EXCLUDED.provider, " +
                "user_id = EXCLUDED.user_id, shop_id = EXCLUDED.shop_id, login_type = EXCLUDED.login_type, " +
                "platform = EXCLUDED.platform, updated_at = now()",
                token.trim(), app.name(), provider.name(), userId, shopId, clip(loginType, 30), clip(platform, 30));
    }

    /** Unknown tokens are a no-op. */
    public void unregister(String token) {
        if (token == null || token.isBlank()) return;
        jdbc.update("DELETE FROM push_device_tokens WHERE token = ?", token.trim());
    }

    /** Partner devices per shop. */
    public Map<UUID, List<Device>> forShops(Collection<UUID> shopIds) {
        return lookup("SELECT shop_id AS k, token, provider FROM push_device_tokens " +
                "WHERE app = 'PARTNER' AND shop_id IN (%s)", null, shopIds);
    }

    /** Devices per user for one app (Customer or Employee). */
    public Map<UUID, List<Device>> forUsers(PushApp app, Collection<UUID> userIds) {
        return lookup("SELECT user_id AS k, token, provider FROM push_device_tokens " +
                "WHERE app = ? AND user_id IN (%s)", app.name(), userIds);
    }

    private Map<UUID, List<Device>> lookup(String sqlTemplate, String app, Collection<UUID> ids) {
        Map<UUID, List<Device>> out = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) return out;
        List<UUID> list = new ArrayList<>(ids.stream().filter(java.util.Objects::nonNull).distinct().toList());
        for (int i = 0; i < list.size(); i += LOOKUP_CHUNK) {
            List<UUID> chunk = list.subList(i, Math.min(i + LOOKUP_CHUNK, list.size()));
            String sql = String.format(sqlTemplate, String.join(",", Collections.nCopies(chunk.size(), "?")));
            List<Object> args = new ArrayList<>();
            if (app != null) args.add(app);
            args.addAll(chunk);
            jdbc.query(sql, rs -> {
                Provider p;
                try { p = Provider.valueOf(rs.getString("provider")); } catch (Exception e) { return; }
                out.computeIfAbsent((UUID) rs.getObject("k"), k -> new ArrayList<>())
                        .add(new Device(rs.getString("token"), p));
            }, args.toArray());
        }
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
