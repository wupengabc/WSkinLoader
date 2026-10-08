package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.wupeng.wskinloader.client.skin.HttpTextureStatus;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    @Final
    private GameProfile profile;

    @Inject(method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;", at = @At("RETURN"))
    private void wskinloader$cacheVanillaSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerNameCache.cache(this.profile);
        PlayerSkin skin = cir.getReturnValue();
        // Default skins have no URL and must never become successful vanilla cache entries.
        if (skin.textureUrl() != null) {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin.texture(), null);
            if (texture instanceof HttpTexture && texture instanceof HttpTextureStatus status
                    && status.wskinloader$isDecoded()) {
                SkinCache.cacheVanillaSkin(this.profile.getId(), skin.texture(), skin.model().id());
            }
        }

        ResourceLocation cape = skin.capeTexture();
        if (cape != null) {
            AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(cape, null);
            if (texture instanceof HttpTexture && texture instanceof HttpTextureStatus status
                    && status.wskinloader$isDecoded()) {
                SkinCache.cacheVanillaCape(this.profile.getId(), cape);
            }
        }
    }
}
