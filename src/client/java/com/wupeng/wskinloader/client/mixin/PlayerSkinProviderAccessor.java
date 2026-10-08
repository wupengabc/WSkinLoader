package com.wupeng.wskinloader.client.mixin;

import net.minecraft.client.resources.SkinManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

/**
 * 暴露原版 {@code SkinManager} 的皮肤缓存目录，用于自定义皮肤的模型类型检测。
 */
@Mixin(SkinManager.class)
public interface PlayerSkinProviderAccessor {

    @Accessor("skinsDirectory")
    File getSkinsDirectory();
}
