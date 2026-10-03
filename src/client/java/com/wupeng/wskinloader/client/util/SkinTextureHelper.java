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

        // 模型类型优先级：显式覆盖 > 解析结果（正版元数据 / 自定义皮肤像素检测）> 原版
        PlayerModelType modelType = original.model();
        String playerName = PlayerNameCache.getName(uuid);
        ModConfig.PlayerOverride override = playerName != null
                ? ModConfig.getInstance().getPlayerOverride(playerName)
                : null;
        if (override != null && !"auto".equals(override.modelType)) {
            modelType = "slim".equals(override.modelType) ? PlayerModelType.SLIM : PlayerModelType.WIDE;
        } else {
            // auto 模式：跟随来源玩家或自定义皮肤解析出的模型类型，
            // 否则窄臂皮肤会套在宽臂模型上导致手臂贴图错位。
            PlayerModelType resolved = SkinCache.getModel(uuid);
            if (resolved != null) {
                modelType = resolved;
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
}
