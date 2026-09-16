package com.wupeng.wskinloader.client.skin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MojangSessionApi {
    private static final Logger LOGGER = LoggerFactory.getLogger("WSkinLoader");
    private static final String SESSION_SERVER = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final Gson GSON = new Gson();
    
    // 缓存查询结果，避免频繁请求
    private static final Map<UUID, ProfileTextures> CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_QUERY_TIME = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FAILURE_CACHE = new ConcurrentHashMap<>();
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
            URL url = new URL(SESSION_SERVER + uuidString);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                int responseCode = connection.getResponseCode();
            
            if (responseCode == 204) {
                // 204 No Content - 玩家没有皮肤数据
                FAILURE_CACHE.put(uuid, System.currentTimeMillis());
                return null;
            }
            
            if (responseCode != 200) {
                LOGGER.warn("无法获取玩家 {} 的纹理数据: HTTP {}", uuid, responseCode);
                try (var errorStream = connection.getErrorStream()) {
                    if (errorStream != null) errorStream.readAllBytes();
                }
                FAILURE_CACHE.put(uuid, System.currentTimeMillis());
                return null;
            }
            
            // 读取响应
            String response;
            try (var stream = connection.getInputStream()) {
                response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            JsonObject json = GSON.fromJson(response, JsonObject.class);
            if (json == null) {
                throw new IllegalArgumentException("Session Server 返回了无效 JSON");
            }
            
            // 解析 properties
            if (!json.has("properties") || json.getAsJsonArray("properties").size() == 0) {
                FAILURE_CACHE.put(uuid, System.currentTimeMillis());
                return null;
            }
            
            JsonObject properties = json.getAsJsonArray("properties").get(0).getAsJsonObject();
            if (!properties.has("value")) {
                throw new IllegalArgumentException("纹理属性缺少 value");
            }
            String value = properties.get("value").getAsString();
            
            // Base64 解码
            String decodedValue = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            JsonObject texturesJson = GSON.fromJson(decodedValue, JsonObject.class);
            if (texturesJson == null) {
                throw new IllegalArgumentException("纹理属性不是有效 JSON");
            }
            
            // 解析纹理
            String skinUrl = null;
            String capeUrl = null;
            boolean isSlim = false;
            
            if (texturesJson.has("textures")) {
                JsonObject textures = texturesJson.getAsJsonObject("textures");
                
                // 皮肤
                if (textures.has("SKIN")) {
                    JsonObject skin = textures.getAsJsonObject("SKIN");
                    skinUrl = skin.get("url").getAsString();
                    
                    // 检查模型类型
                    if (skin.has("metadata")) {
                        JsonObject metadata = skin.getAsJsonObject("metadata");
                        if (metadata.has("model")) {
                            String model = metadata.get("model").getAsString();
                            isSlim = "slim".equalsIgnoreCase(model);
                        }
                    }
                    // 如果没有 metadata，默认为 wide (Steve)
                }
                
                // 披风
                if (textures.has("CAPE")) {
                    JsonObject cape = textures.getAsJsonObject("CAPE");
                    capeUrl = cape.get("url").getAsString();
                }
            }
            
            // 如果没有皮肤 URL，返回 null
            if (skinUrl == null && capeUrl == null) {
                FAILURE_CACHE.put(uuid, System.currentTimeMillis());
                return null;
            }
            
            ProfileTextures result = new ProfileTextures(skinUrl, capeUrl, isSlim);
            
            // 缓存结果
            CACHE.put(uuid, result);
            LAST_QUERY_TIME.put(uuid, System.currentTimeMillis());
            
            return result;
            
            } finally {
                connection.disconnect();
            }
        } catch (Exception e) {
            LOGGER.error("获取玩家 {} 的纹理数据时出错: {}", uuid, e.getMessage());
            FAILURE_CACHE.put(uuid, System.currentTimeMillis());
            return null;
        }
    }
    
    public static void clearCache() {
        CACHE.clear();
        LAST_QUERY_TIME.clear();
        FAILURE_CACHE.clear();
    }
}
