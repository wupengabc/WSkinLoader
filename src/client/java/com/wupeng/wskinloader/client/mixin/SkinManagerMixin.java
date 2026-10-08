package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.wupeng.wskinloader.client.skin.SkinCacheDirectory;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * 暴露原版 {@code SkinManager} 的皮肤缓存目录，用于自定义皮肤的模型类型检测。
 */
@Mixin(SkinManager.class)
public abstract class SkinManagerMixin implements SkinCacheDirectory {
    @Unique
    private File wskinloader$skinsDirectory;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wskinloader$captureCacheDirectory(TextureManager textures, Path root,
                                                   MinecraftSessionService session, Executor executor,
                                                   CallbackInfo ci) {
        this.wskinloader$skinsDirectory = root.toFile();
    }

    @Override
    public File wskinloader$getSkinsDirectory() {
        return this.wskinloader$skinsDirectory;
    }
}
