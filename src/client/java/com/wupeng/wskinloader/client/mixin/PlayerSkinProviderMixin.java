package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin {
    
    @Shadow @Final private SkinTextureDownloader skinTextureDownloader;
    
    @Inject(method = "get(Lcom/mojang/authlib/GameProfile;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void onGet(GameProfile profile, CallbackInfoReturnable<?> cir) {
        PlayerNameCache.cache(profile);
        SkinLoader.loadSkinForProfile(profile, skinTextureDownloader);
    }
}
