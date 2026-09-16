package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/**
 * Makes the tab list head use the skin resolved by WSkinLoader.
 *
 * <p>{@link PlayerTabOverlay} draws each head from
 * {@code info.getSkin().body().texturePath()}. On offline / third-party servers
 * that skin is the default one, so the head would not match the custom skin that
 * is already shown on the player model. Redirecting the call site feeds the
 * cached skin instead.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlaySkinMixin {

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/PlayerInfo;getSkin()Lnet/minecraft/world/entity/player/PlayerSkin;"
            )
    )
    private PlayerSkin wskinloader$tabListSkin(PlayerInfo info) {
        PlayerSkin original = info.getSkin();
        if (original == null || !ModConfig.getInstance().enableTabListHeads) {
            return original;
        }

        UUID uuid = info.getProfile().id();
        ClientAsset.Texture customSkin = SkinCache.getSkin(uuid);
        if (customSkin == null) {
            return original;
        }

        ClientAsset.Texture customCape = SkinCache.getCape(uuid);
        return new PlayerSkin(
                customSkin,
                customCape != null ? customCape : original.cape(),
                original.elytra(),
                original.model(),
                original.secure()
        );
    }
}
