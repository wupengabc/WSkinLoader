package com.wupeng.wskinloader.client.skin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.wupeng.wskinloader.client.util.BoundedCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class MojangSessionApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("WSkinLoader");
    private static final String SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Gson GSON = new Gson();

    // 使用有界缓存，避免内存无限增长
    private static final Map<UUID, ProfileTextures> CACHE = Collections.synchronizedMap(new BoundedCache<>(1000));
    private static final Map<UUID, Long> LAST_QUERY_TIME = Collections.synchronizedMap(new BoundedCache<>(1000));
    private static final Map<UUID, Long> FAILURE_CACHE = Collections.synchronizedMap(new BoundedCache<>(500));
    private static final long CACHE_DURATION = 60000; // 1分钟缓存
    private static final long FAILURE_CACHE_DURATION = 30000L;
    
    public static class ProfileTextures {
        public String skinUrl;
        public String capeUrl;
        public boolean isSlim; // true = slim (Alex), false = wide (Steve)
        
        public ProfileTextures(String skinUrl, String capeUrl, boolean isSlim) {
            this.skinUrl = skinUrl;
            this.capeUrl = capeUrl;
            this.isSlim = isSlim;
        }
    }
    
    /**
     * 获取玩家的皮肤和披风信息
     * @param uuid 玩家 UUID
     * @return ProfileTextures 或 null（如果获取失败）
     */
    public static ProfileTextures getProfileTextures(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        // 检查缓存
        Long lastQuery = LAST_QUERY_TIME.get(uuid);
        if (lastQuery != null && System.currentTimeMillis() - lastQuery < CACHE_DURATION) {
            ProfileTextures cached = CACHE.get(uuid);
            if (cached != null) {
                return cached;
            }
        }
        Long failedAt = FAILURE_CACHE.get(uuid);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_CACHE_DURATION) {
            return null;
        }
        
        try {
            // 移除 UUID 中的连字符
            String uuidString = uuid.toString().replace("-", "");
            URL url = java.net.URI.create(SESSION_SERVER + uuidString).toURL();
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(false);
                int responseCode = connection.getResponseCode();
            
            if (responseCode == 204) {
                // 204 No Content - 玩家没有皮肤数据
                ProfileTextures empty = new ProfileTextures(null, null, false);
                CACHE.put(uuid, empty);
                LAST_QUERY_TIME.put(uuid, System.currentTimeMillis());
                return empty;
            }
            
            if (responseCode != 200) {
                LOGGER.warn("无法获取玩家 {} 的纹理数据: HTTP {}", uuid, responseCode);
                FAILURE_CACHE.put(uuid, System.currentTimeMillis());
                return null;
            }
            
            // 读取响应
            String response;
            try (var stream = connection.getInputStream()) {
                byte[] bytes = stream.readNBytes(65537);
                if (bytes.length > 65536) throw new java.io.IOException("Texture response too large");
                response = new String(bytes, StandardCharsets.UTF_8);
            }
            ProfileTextures result = parseProfileTextures(response);
            
            // 缓存结果
            CACHE.put(uuid, result);
            LAST_QUERY_TIME.put(uuid, System.currentTimeMillis());
            
            return result;
            
            } finally {
                connection.disconnect();
            }
        } catch (java.io.IOException e) {
            LOGGER.warn("无法获取玩家 {} 的纹理数据（网络错误）: {}", uuid, e.getMessage());
            LOGGER.debug("详细错误信息", e);
            FAILURE_CACHE.put(uuid, System.currentTimeMillis());
            return null;
        } catch (Exception e) {
            LOGGER.error("获取玩家 {} 的纹理数据时发生意外错误", uuid, e);
            FAILURE_CACHE.put(uuid, System.currentTimeMillis());
            return null;
        }
    }

    static ProfileTextures parseProfileTextures(String response) {
        JsonObject json = GSON.fromJson(response, JsonObject.class);
        if (json == null) {
            throw new IllegalArgumentException("Session Server 返回了无效 JSON");
        }
        if (!json.has("properties")) {
            return new ProfileTextures(null, null, false);
        }

        if (!json.get("properties").isJsonArray()) {
            throw new IllegalArgumentException("纹理属性不是数组");
        }
        JsonObject properties = null;
        for (var element : json.getAsJsonArray("properties")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("纹理属性不是对象");
            }
            JsonObject property = element.getAsJsonObject();
            if (!property.has("name") || !property.get("name").isJsonPrimitive()
                    || !property.get("name").getAsJsonPrimitive().isString() || !property.has("value")) {
                throw new IllegalArgumentException("纹理属性缺少 name 或 value");
            }
            if ("textures".equals(property.get("name").getAsString())) {
                properties = property;
                break;
            }
        }
        if (properties == null) {
            return new ProfileTextures(null, null, false);
        }
        String decodedValue;
        try {
            decodedValue = new String(Base64.getDecoder().decode(properties.get("value").getAsString()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("纹理属性不是有效 Base64", e);
        }
        JsonObject texturesJson = GSON.fromJson(decodedValue, JsonObject.class);
        if (texturesJson == null) {
            throw new IllegalArgumentException("纹理属性不是有效 JSON");
        }

        String skinUrl = null;
        String capeUrl = null;
        boolean isSlim = false;
        if (texturesJson.has("textures")) {
            if (!texturesJson.get("textures").isJsonObject()) {
                throw new IllegalArgumentException("纹理数据不是对象");
            }
            JsonObject textures = texturesJson.getAsJsonObject("textures");
            if (textures.has("SKIN")) {
                JsonObject skin = textures.getAsJsonObject("SKIN");
                if (!skin.has("url")) {
                    throw new IllegalArgumentException("皮肤纹理缺少 url");
                }
                skinUrl = skin.get("url").getAsString();
                if (skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")) {
                    isSlim = "slim".equalsIgnoreCase(skin.getAsJsonObject("metadata").get("model").getAsString());
                }
            }
            if (textures.has("CAPE")) {
                JsonObject cape = textures.getAsJsonObject("CAPE");
                if (!cape.has("url")) {
                    throw new IllegalArgumentException("披风纹理缺少 url");
                }
                capeUrl = cape.get("url").getAsString();
            }
        }
        return new ProfileTextures(skinUrl, capeUrl, isSlim);
    }
    
    public static void clearCache() {
        CACHE.clear();
        LAST_QUERY_TIME.clear();
        FAILURE_CACHE.clear();
    }
}
