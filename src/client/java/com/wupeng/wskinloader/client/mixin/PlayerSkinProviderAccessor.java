package com.wupeng.wskinloader.client.mixin;

import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SkinManager.class)
public interface PlayerSkinProviderAccessor {
    
    @Accessor("skinTextureDownloader")
    SkinTextureDownloader getDownloader();
}
