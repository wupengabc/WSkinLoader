package com.wupeng.wskinloader.client.skin;

import net.minecraft.core.ClientAsset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinCache {
    private static final Map<UUID, ClientAsset.Texture> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ClientAsset.Texture> CAPE_CACHE = new ConcurrentHashMap<>();
    
    public static void cacheSkin(UUID uuid, ClientAsset.Texture skinAsset) {
        if (skinAsset != null) {
            SKIN_CACHE.put(uuid, skinAsset);
        }
    }
    
    public static void cacheCape(UUID uuid, ClientAsset.Texture capeAsset) {
        if (capeAsset != null) {
            CAPE_CACHE.put(uuid, capeAsset);
        }
    }
    
    public static ClientAsset.Texture getSkin(UUID uuid) {
        return SKIN_CACHE.get(uuid);
    }
    
    public static ClientAsset.Texture getCape(UUID uuid) {
        return CAPE_CACHE.get(uuid);
    }
    
    /**
     * 删除指定玩家的皮肤缓存
     */
    public static void removeSkin(UUID uuid) {
        SKIN_CACHE.remove(uuid);
    }
    
    /**
     * 删除指定玩家的披风缓存
     */
    public static void removeCape(UUID uuid) {
        CAPE_CACHE.remove(uuid);
    }
    
    /**
     * 删除指定玩家的所有缓存（皮肤和披风）
     */
    public static void removePlayer(UUID uuid) {
        SKIN_CACHE.remove(uuid);
        CAPE_CACHE.remove(uuid);
    }
    
    /**
     * 获取所有已缓存皮肤的玩家 UUID
     */
    public static Set<UUID> getCachedPlayers() {
        return SKIN_CACHE.keySet();
    }
    
    /**
     * 检查是否有该玩家的皮肤缓存
     */
    public static boolean hasSkin(UUID uuid) {
        return SKIN_CACHE.containsKey(uuid);
    }
    
    /**
     * 检查是否有该玩家的披风缓存
     */
    public static boolean hasCape(UUID uuid) {
        return CAPE_CACHE.containsKey(uuid);
    }
    
    public static void clear() {
        SKIN_CACHE.clear();
        CAPE_CACHE.clear();
    }
}
