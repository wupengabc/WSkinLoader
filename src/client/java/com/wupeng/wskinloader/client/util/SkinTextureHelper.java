package com.wupeng.wskinloader.client.util;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * 工具类：把 {@link SkinCache} 里解析出的自定义纹理 / 模型套用到客户端玩家上。
 *
 * <p>Preserves vanilla metadata when applying custom textures to a PlayerSkin.
 */
public class SkinTextureHelper {
    private static final ThreadLocal<PreviewCape> PREVIEW_CAPE = new ThreadLocal<>();

    /** A null texture explicitly means no cape during this preview frame. */
    public record PreviewCape(UUID uuid, ResourceLocation texture) {}

    public static PreviewCape setPreviewCape(PreviewCape preview) {
        PreviewCape previous = PREVIEW_CAPE.get();
        if (preview == null) {
            PREVIEW_CAPE.remove();
        } else {
            PREVIEW_CAPE.set(preview);
        }
        return previous;
    }

    /** 自定义皮肤纹理；没有则返回 {@code null}。 */
    public static ResourceLocation customSkin(UUID uuid) {
        return uuid == null ? null : SkinCache.getSkin(uuid);
    }

    /** 自定义披风纹理；没有则返回 {@code null}。 */
    public static ResourceLocation customCape(UUID uuid) {
        return uuid == null ? null : SkinCache.getCape(uuid);
    }

    public static PlayerSkin resolvePlayerSkin(UUID uuid, PlayerSkin original) {
        ResourceLocation custom = customSkin(uuid);
        ResourceLocation texture = custom != null ? custom : original.texture();
        ResourceLocation cape = resolveCapeTexture(uuid, original.capeTexture());
        PlayerSkin.Model model = PlayerSkin.Model.byName(resolveModelName(uuid, original.model().id()));
        if (Objects.equals(texture, original.texture())
                && Objects.equals(cape, original.capeTexture()) && model == original.model()) {
            return original;
        }
        return new PlayerSkin(texture, original.textureUrl(), cape, original.elytraTexture(),
                model, original.secure());
    }

    public static ResourceLocation resolveCapeTexture(UUID uuid, ResourceLocation original) {
        PreviewCape preview = PREVIEW_CAPE.get();
        if (preview != null && preview.uuid().equals(uuid)) {
            return preview.texture();
        }
        ResourceLocation custom = customCape(uuid);
        return custom != null ? custom : original;
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
