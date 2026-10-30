package com.wupeng.wskinloader.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.renderer.texture.TextureManager;
import java.util.concurrent.Executor;

@Mixin(SkinTextureDownloader.class)
public interface SkinTextureDownloaderAccessor {
    @Accessor("textureManager")
    TextureManager wskinloader$getTextureManager();

    @Accessor("mainThreadExecutor")
    Executor wskinloader$getMainThreadExecutor();

    @Invoker("processLegacySkin")
    static NativeImage wskinloader$processLegacySkin(NativeImage image, String url) {
        throw new AssertionError();
    }
}
