package com.wupeng.wskinloader.client.util;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
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
        
        // 检查是否有模型类型覆盖
        PlayerModelType modelType = original.model();
        String playerName = PlayerNameCache.getName(uuid);
        if (playerName != null) {
            ModConfig.PlayerOverride override = ModConfig.getInstance().getPlayerOverride(playerName);
            if (override != null && !"auto".equals(override.modelType)) {
                modelType = "slim".equals(override.modelType) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
            } else if (override != null && (hasText(override.skinSourcePlayer) || hasText(override.capeSourcePlayer))) {
                // 玩家映射：跟随来源玩家解析出的模型类型
                PlayerModelType sourceModel = SkinCache.getModel(uuid);
                if (sourceModel != null) {
                    modelType = sourceModel;
                }
            }
        }
        
        if (customSkin == null && customCape == null && modelType == original.model()) {
            return null;
        }
        
        return new PlayerSkin(
            customSkin != null ? customSkin : original.body(),
            customCape != null ? customCape : original.cape(),
            original.elytra(),
            modelType,
            original.secure()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
