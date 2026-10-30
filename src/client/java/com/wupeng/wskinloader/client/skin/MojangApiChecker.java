package com.wupeng.wskinloader.client.skin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wupeng.wskinloader.client.util.BoundedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class MojangApiChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("WSkinLoader");
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Gson GSON = new Gson();

    // 使用有界缓存，最多缓存 1000 个玩家档案
    private static final Map<String, CachedProfile> CACHE = Collections.synchronizedMap(new BoundedCache<>(1000));
    private static final Map<String, Long> FAILURE_CACHE = Collections.synchronizedMap(new BoundedCache<>(500));
    private static final long PROFILE_CACHE_DURATION = 60 * 60 * 1000L;
    private static final long MISSING_PROFILE_CACHE_DURATION = 5 * 60 * 1000L;
    private static final long FAILURE_CACHE_DURATION = 30000L;

    static final class CachedProfile {
        final PlayerProfile profile;
        final long cachedAt;

        CachedProfile(PlayerProfile profile, long cachedAt) {
            this.profile = profile;
            this.cachedAt = cachedAt;
        }

        boolean isFresh(long now) {
            long duration = profile.isPremium ? PROFILE_CACHE_DURATION : MISSING_PROFILE_CACHE_DURATION;
            return now - cachedAt < duration;
        }
    }
    
    public static class PlayerProfile {
        public final UUID uuid;
        public final String name;
        public final boolean isPremium;
        
        public PlayerProfile(UUID uuid, String name, boolean isPremium) {
            this.uuid = uuid;
            this.name = name;
            this.isPremium = isPremium;
        }
    }
    
    /**
     * 检查玩家是否为正版玩家并获取 UUID
     * @param playerName 玩家名
     * @return 非正版账号返回 isPremium=false；查询失败返回 null
     */
    public static PlayerProfile getPlayerProfile(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        String cacheKey = playerName.toLowerCase(java.util.Locale.ROOT);
        // 先检查缓存
        CachedProfile cached = CACHE.get(cacheKey);
        if (cached != null) {
            long now = System.currentTimeMillis();
            if (cached.isFresh(now)) {
                return cached.profile;
            }
            CACHE.remove(cacheKey, cached);
        }
        Long failedAt = FAILURE_CACHE.get(cacheKey);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_CACHE_DURATION) {
            return null;
        }
        
        try {
            URL url = java.net.URI.create(MOJANG_API + java.net.URLEncoder.encode(playerName, StandardCharsets.UTF_8)).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.setReadTimeout(3000);
                connection.setInstanceFollowRedirects(false);
                int responseCode = connection.getResponseCode();

                if (responseCode == 200) {
                // 正版玩家，解析 UUID
                String response;
                try (var stream = connection.getInputStream()) {
                    byte[] bytes = stream.readNBytes(65537);
                    if (bytes.length > 65536) throw new java.io.IOException("Profile response too large");
                    response = new String(bytes, StandardCharsets.UTF_8);
                }
                PlayerProfile profile = parseProfile(response);
                CACHE.put(cacheKey, new CachedProfile(profile, System.currentTimeMillis()));
                return profile;
            } else if (responseCode == 404 || responseCode == 204) {
                // 离线玩家
                PlayerProfile profile = new PlayerProfile(null, playerName, false);
                CACHE.put(cacheKey, new CachedProfile(profile, System.currentTimeMillis()));
                return profile;
            } else {
                LOGGER.warn("无法检查玩家 {} 的正版状态: HTTP {}", playerName, responseCode);
                FAILURE_CACHE.put(cacheKey, System.currentTimeMillis());
                return null;
            }
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            LOGGER.warn("无法检查玩家 {} 的正版状态: {}", playerName, e.getMessage());
            FAILURE_CACHE.put(cacheKey, System.currentTimeMillis());
            return null;
        }
    }
    
    /**
     * 检查玩家是否为正版玩家（简化版本）
     * @param playerName 玩家名
     * @return true = 正版玩家, false = 离线玩家
     */
    public static boolean isPremiumPlayer(String playerName) {
        PlayerProfile profile = getPlayerProfile(playerName);
        return profile != null && profile.isPremium;
    }
    
    /**
     * 将不带连字符的 UUID 字符串转换为标准 UUID
     */
    private static UUID parseUUID(String uuidString) {
        if (uuidString.length() == 32) {
            // 添加连字符: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
            String formatted = uuidString.substring(0, 8) + "-" +
                             uuidString.substring(8, 12) + "-" +
                             uuidString.substring(12, 16) + "-" +
                             uuidString.substring(16, 20) + "-" +
                             uuidString.substring(20, 32);
            return UUID.fromString(formatted);
        }
        return UUID.fromString(uuidString);
    }

    static PlayerProfile parseProfile(String response) {
        JsonObject json = GSON.fromJson(response, JsonObject.class);
        if (json == null || !json.has("id") || !json.has("name")
                || !json.get("id").isJsonPrimitive() || !json.get("id").getAsJsonPrimitive().isString()
                || !json.get("name").isJsonPrimitive() || !json.get("name").getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Mojang API 返回了无效 JSON");
        }
        return new PlayerProfile(parseUUID(json.get("id").getAsString()), json.get("name").getAsString(), true);
    }
    
    public static void clearCache() {
        CACHE.clear();
        FAILURE_CACHE.clear();
    }
}
