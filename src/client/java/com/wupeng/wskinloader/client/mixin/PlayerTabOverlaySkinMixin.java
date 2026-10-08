package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * 让 Tab 列表中的玩家头像使用 WSkinLoader 解析出的皮肤。
 *
 * <p>原版 {@code PlayerTabOverlay} 通过 {@code info.getSkinLocation()} 取得
 * 头像纹理；离线 / 第三方服务器上那是默认皮肤，与玩家模型上已经显示的自定义
 * 皮肤不一致。重定向该调用点即可喂入缓存里的皮肤。
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlaySkinMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkinLocation()Lnet/minecraft/resources/ResourceLocation;"
        )
    )
    private ResourceLocation wskinloader$tabListSkin(PlayerInfo info) {
        ResourceLocation original = info.getSkinLocation();
        ModConfig config = ModConfig.getInstance();
        if (original == null || !config.enableTabListHeads) {
            return original;
        }
        UUID uuid = info.getProfile().getId();
        ResourceLocation custom = uuid == null ? null : SkinCache.getSkin(uuid);
        return custom != null ? custom : original;
    }
}
