package com.wupeng.wskinloader.client.skin;

import net.minecraft.core.ClientAsset;
import java.util.Map;
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
    
    public static void clear() {
        SKIN_CACHE.clear();
        CAPE_CACHE.clear();
    }
}
