package com.wupeng.wskinloader.client.skin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 缓存每个玩家的皮肤来源信息（用于在名称标签旁显示）
 */
public class PlayerSkinSourceCache {
    private static final Map<UUID, String> SKIN_SOURCE = new ConcurrentHashMap<>();
    
    /**
     * 设置玩家的皮肤来源标签
     * @param uuid 玩家UUID
     * @param source 来源标签（如"LittleSkin"、"正版"等）
     */
    public static void setSource(UUID uuid, String source) {
        if (uuid != null && source != null) {
            SKIN_SOURCE.put(uuid, source);
        }
    }
    
    /**
     * 获取玩家的皮肤来源标签
     * @param uuid 玩家UUID
     * @return 来源标签，如果未设置则返回null
     */
    public static String getSource(UUID uuid) {
        return SKIN_SOURCE.get(uuid);
    }
    
    /**
     * 移除指定玩家的来源缓存
     */
    public static void remove(UUID uuid) {
        SKIN_SOURCE.remove(uuid);
    }
    
    /**
     * 清除所有缓存
     */
    public static void clear() {
        SKIN_SOURCE.clear();
    }
}
