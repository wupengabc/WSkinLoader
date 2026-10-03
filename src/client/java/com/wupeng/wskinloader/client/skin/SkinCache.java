package com.wupeng.wskinloader.client.skin;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 已解析的自定义皮肤 / 披风纹理缓存。
 *
 * <p>1.16.5 没有 {@code ClientAsset}/{@code PlayerSkin}，纹理就是
 * {@link ResourceLocation}，模型类型是 {@code "default"}/{@code "slim"} 字符串。
 */
public class SkinCache {
    private static final Map<UUID, ResourceLocation> SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceLocation> CAPE_CACHE = new ConcurrentHashMap<>();
    /** 解析出的模型类型（用于玩家映射时跟随来源玩家的粗细模型）。 */
    private static final Map<UUID, String> MODEL_CACHE = new ConcurrentHashMap<>();

    public static void cacheModel(UUID uuid, String modelType) {
        if (uuid != null && modelType != null) {
            MODEL_CACHE.put(uuid, modelType);
        }
    }

    public static String getModel(UUID uuid) {
        return uuid == null ? null : MODEL_CACHE.get(uuid);
    }

    public static void cacheSkin(UUID uuid, ResourceLocation skinTexture) {
        if (skinTexture != null) {
            SKIN_CACHE.put(uuid, skinTexture);
        }
    }

    public static void cacheCape(UUID uuid, ResourceLocation capeTexture) {
        if (capeTexture != null) {
            CAPE_CACHE.put(uuid, capeTexture);
        }
    }

    public static ResourceLocation getSkin(UUID uuid) {
        return SKIN_CACHE.get(uuid);
    }

    public static ResourceLocation getCape(UUID uuid) {
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
     * 删除指定玩家的所有缓存（皮肤、披风和模型）
     */
    public static void removePlayer(UUID uuid) {
        SKIN_CACHE.remove(uuid);
        CAPE_CACHE.remove(uuid);
        MODEL_CACHE.remove(uuid);
    }

    /**
     * 删除指定玩家的模型缓存
     */
    public static void removeModel(UUID uuid) {
        MODEL_CACHE.remove(uuid);
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
        MODEL_CACHE.clear();
    }
}
