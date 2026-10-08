package com.wupeng.wskinloader.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import com.wupeng.wskinloader.client.skin.HttpTextureStatus;
import net.minecraft.client.renderer.texture.HttpTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HttpTexture.class)
public abstract class HttpTextureMixin implements HttpTextureStatus {
    @Unique
    private boolean wskinloader$decoded;

    @Inject(method = "loadCallback(Lcom/mojang/blaze3d/platform/NativeImage;)V", at = @At("HEAD"))
    private void wskinloader$markDecoded(NativeImage image, CallbackInfo ci) {
        // Mark success before onDownloaded runs; fallback texture uploads do not reach here.
        this.wskinloader$decoded = true;
    }

    @Override
    public boolean wskinloader$isDecoded() {
        return this.wskinloader$decoded;
    }
}
