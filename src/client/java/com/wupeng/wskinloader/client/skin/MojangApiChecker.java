package com.wupeng.wskinloader.client.skin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MojangApiChecker {
    private static final Logger LOGGER = LoggerFactory.getLogger("WSkinLoader");
    private static final String MOJANG_API = "https://api.mojang.com/users/profiles/minecraft/";
    private static final Gson GSON = new Gson();
    private static final Map<String, PlayerProfile> CACHE = new ConcurrentHashMap<>();
    
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
     * @return PlayerProfile 或 null（如果是离线玩家）
     */
    public static PlayerProfile getPlayerProfile(String playerName) {
        // 先检查缓存
        PlayerProfile cached = CACHE.get(playerName);
        if (cached != null) {
            return cached;
        }
        
        try {
            URL url = new URL(MOJANG_API + playerName);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == 200) {
                // 正版玩家，解析 UUID
                String response = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject json = GSON.fromJson(response, JsonObject.class);
                
                String uuidString = json.get("id").getAsString();
                String name = json.get("name").getAsString();
                
                // 将 UUID 字符串转换为标准格式（添加连字符）
                UUID uuid = parseUUID(uuidString);
                
                PlayerProfile profile = new PlayerProfile(uuid, name, true);
                CACHE.put(playerName, profile);
                return profile;
            } else if (responseCode == 404) {
                // 离线玩家
                PlayerProfile profile = new PlayerProfile(null, playerName, false);
                CACHE.put(playerName, profile);
                return profile;
            } else {
                LOGGER.warn("无法检查玩家 {} 的正版状态: HTTP {}", playerName, responseCode);
                return null;
            }
        } catch (Exception e) {
            LOGGER.warn("无法检查玩家 {} 的正版状态: {}", playerName, e.getMessage());
            // 网络错误时假设为离线玩家
            return new PlayerProfile(null, playerName, false);
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
    
    public static void clearCache() {
        CACHE.clear();
    }
}
