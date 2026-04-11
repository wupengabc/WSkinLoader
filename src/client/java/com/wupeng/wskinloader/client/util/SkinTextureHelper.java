package com.wupeng.wskinloader.client.util;

import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.UUID;

/**
 * 工具类：用于创建修改后的 PlayerSkin
 */
public class SkinTextureHelper {
    
    /**
     * 根据缓存中的自定义皮肤和披风创建新的 PlayerSkin
     * 
     * @param uuid 玩家 UUID
     * @param original 原始的 PlayerSkin
     * @return 如果有自定义皮肤或披风，返回修改后的 PlayerSkin；否则返回 null
     */
    public static PlayerSkin createModifiedSkin(UUID uuid, PlayerSkin original) {
        if (uuid == null || original == null) {
            return null;
        }
        
        ClientAsset.Texture customSkin = SkinCache.getSkin(uuid);
        ClientAsset.Texture customCape = SkinCache.getCape(uuid);
        
        if (customSkin == null && customCape == null) {
            return null;
        }
        
        return new PlayerSkin(
            customSkin != null ? customSkin : original.body(),
            customCape != null ? customCape : original.cape(),
            original.elytra(),
            original.model(),
            original.secure()
        );
    }
}
