package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerNameCache {
    private static final Map<UUID, String> UUID_TO_NAME = new ConcurrentHashMap<>();
    
    public static void cache(GameProfile profile) {
        if (profile != null && profile.id() != null && profile.name() != null) {
            UUID_TO_NAME.put(profile.id(), profile.name());
        }
    }
    
    public static String getName(UUID uuid) {
        return UUID_TO_NAME.get(uuid);
    }
    
    public static void clear() {
        UUID_TO_NAME.clear();
    }
}
