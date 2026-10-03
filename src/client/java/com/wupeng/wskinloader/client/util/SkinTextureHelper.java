package com.wupeng.wskinloader.client.util;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 工具类：把 {@link SkinCache} 里解析出的自定义纹理 / 模型套用到客户端玩家上。
 *
 * <p>1.16.5 没有 {@code PlayerSkin} 聚合类型，皮肤的各个部分由
 * {@code AbstractClientPlayer} 的独立方法返回，因此这里按部分拆分。
 */
public class SkinTextureHelper {

    /** 自定义皮肤纹理；没有则返回 {@code null}。 */
    public static ResourceLocation customSkin(UUID uuid) {
        return uuid == null ? null : SkinCache.getSkin(uuid);
    }

    /** 自定义披风纹理；没有则返回 {@code null}。 */
    public static ResourceLocation customCape(UUID uuid) {
        return uuid == null ? null : SkinCache.getCape(uuid);
    }

    /**
     * 解析模型类型（{@code "default"} / {@code "slim"}）。
     *
     * <p>优先级：显式覆盖 &gt; 已解析结果（正版元数据 / 自定义皮肤像素检测）&gt; 原版值。
     */
    public static String resolveModelName(UUID uuid, String original) {
        if (uuid == null) {
            return original;
        }
        String playerName = PlayerNameCache.getName(uuid);
        ModConfig.PlayerOverride override = playerName != null
                ? ModConfig.getInstance().getPlayerOverride(playerName)
                : null;
        if (override != null && !"auto".equals(override.modelType)) {
            return "slim".equals(override.modelType) ? "slim" : "default";
        }
        String resolved = SkinCache.getModel(uuid);
        return resolved != null ? resolved : original;
    }
}
