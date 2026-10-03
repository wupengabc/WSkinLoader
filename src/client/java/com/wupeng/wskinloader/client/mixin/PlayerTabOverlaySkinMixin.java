package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.SuppressedHeadState;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.util.ComponentFaceDetector;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
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
 *
 * <p>This is the only redirect on that call site. Duplicate-head suppression is
 * decided here as well, because the draw call immediately follows it in the same
 * per-player loop iteration: see {@link PlayerFaceExtractorMixin}.
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
        ModConfig config = ModConfig.getInstance();

        if (original == null || !config.enableTabListHeads) {
            SuppressedHeadState.clear();
            return original;
        }

        UUID uuid = info.getProfile().id();
        ClientAsset.Texture customSkin = SkinCache.getSkin(uuid);
        boolean duplicate = config.skipDuplicateTabHead
                && ComponentFaceDetector.containsPlayerFace(ComponentFaceDetector.renderableTabName(info));

        // The suppressed texture is the one the head draw will actually use, so
        // it must be resolved after the custom skin is known.
        Identifier headTexture = customSkin != null
                ? customSkin.texturePath()
                : original.body().texturePath();
        if (duplicate) {
            SuppressedHeadState.set(headTexture);
        } else {
            SuppressedHeadState.clear();
        }

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
