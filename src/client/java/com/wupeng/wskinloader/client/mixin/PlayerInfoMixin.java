package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.skin.HttpTextureStatus;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.AbstractTexture;
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

    @Shadow
    @Final
    private Map<MinecraftProfileTexture.Type, ResourceLocation> textureLocations;

    @Shadow
    private String skinModel;

    @Inject(method = "getSkinLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"))
    private void wskinloader$cacheVanillaSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        PlayerNameCache.cache(this.profile);
        ResourceLocation location = this.textureLocations.get(MinecraftProfileTexture.Type.SKIN);
        if (location == null) {
            return;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location, null);
        if (texture instanceof HttpTextureStatus && ((HttpTextureStatus) texture).wskinloader$isDecoded()) {
            SkinCache.cacheVanillaSkin(this.profile.getId(), location, this.skinModel == null ? "default" : this.skinModel);
        }
    }

    @Inject(method = "getCapeLocation()Lnet/minecraft/resources/ResourceLocation;", at = @At("RETURN"))
    private void wskinloader$cacheVanillaCape(CallbackInfoReturnable<ResourceLocation> cir) {
        PlayerNameCache.cache(this.profile);
        ResourceLocation location = this.textureLocations.get(MinecraftProfileTexture.Type.CAPE);
        if (location == null) {
            return;
        }

        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(location, null);
        if (texture instanceof HttpTextureStatus && ((HttpTextureStatus) texture).wskinloader$isDecoded()) {
            SkinCache.cacheVanillaCape(this.profile.getId(), location);
        }
    }
}
