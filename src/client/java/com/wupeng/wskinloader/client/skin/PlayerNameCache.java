package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerNameCache {
    private static final Map<UUID, String> UUID_TO_NAME = new ConcurrentHashMap<>();
    
    public static void cache(GameProfile profile) {
        if (profile != null && profile.getId() != null && profile.getName() != null) {
            UUID_TO_NAME.put(profile.getId(), profile.getName());
        }
    }
    
    /**
     * 缓存玩家名称
     */
    public static void cacheName(UUID uuid, String name) {
        if (uuid != null && name != null) {
            UUID_TO_NAME.put(uuid, name);
        }
    }
    
    public static String getName(UUID uuid) {
        return UUID_TO_NAME.get(uuid);
    }

    /** Snapshot of all known uuid to name mappings, used when reloading every player. */
    public static Map<UUID, String> snapshot() {
        return new java.util.HashMap<>(UUID_TO_NAME);
    }
    
    /**
     * 移除指定玩家的名称缓存
     */
    public static void remove(UUID uuid) {
        UUID_TO_NAME.remove(uuid);
    }
    
    public static void clear() {
        UUID_TO_NAME.clear();
    }
}
