package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 PlayerSkinRenderCache.RenderInfo，让物品栏中的头颅也能显示自定义皮肤
 */
@Mixin(PlayerSkinRenderCache.RenderInfo.class)
public abstract class PlayerSkinCacheEntryMixin {
    
    @Shadow @Final private GameProfile gameProfile;
    @Shadow @Final private PlayerSkin playerSkin;
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(PlayerSkinRenderCache outer, GameProfile gameProfile, PlayerSkin playerSkin, PlayerSkin.Patch patch, CallbackInfo ci) {
        if (gameProfile == null || gameProfile.id() == null) {
            return;
        }
        
        if (SkinCache.getSkin(gameProfile.id()) == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSkinManager() != null) {
                PlayerSkinProviderAccessor accessor = (PlayerSkinProviderAccessor) client.getSkinManager();
                SkinLoader.loadSkinForProfile(gameProfile, accessor.getDownloader());
            }
        }
    }
    
    @Inject(method = "playerSkin", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (gameProfile == null || gameProfile.id() == null) {
            return;
        }
        
        ClientAsset.Texture customSkin = SkinCache.getSkin(gameProfile.id());
        ClientAsset.Texture customCape = SkinCache.getCape(gameProfile.id());
        
        if (customSkin != null || customCape != null) {
            PlayerSkin original = cir.getReturnValue();
            cir.setReturnValue(new PlayerSkin(
                customSkin != null ? customSkin : original.body(),
                customCape != null ? customCape : original.cape(),
                original.elytra(),
                original.model(),
                original.secure()
            ));
        }
    }
    
    @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
    private void onRenderType(CallbackInfoReturnable<RenderType> cir) {
        if (gameProfile == null || gameProfile.id() == null) {
            return;
        }
        
        ClientAsset.Texture customSkin = SkinCache.getSkin(gameProfile.id());
        
        if (customSkin != null) {
            cir.setReturnValue(SkullBlockRenderer.getPlayerSkinRenderType(customSkin.texturePath()));
        }
    }
}
