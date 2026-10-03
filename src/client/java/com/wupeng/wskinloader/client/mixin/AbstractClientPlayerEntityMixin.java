package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 用 WSkinLoader 解析出的自定义皮肤 / 披风替换客户端玩家的纹理。
 *
 * <p>1.16.5 的 {@code AbstractClientPlayer} 分别暴露皮肤、披风与模型名三个
 * getter，因此分别注入。
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("RETURN"), cancellable = true)
    private void wskinloader$customSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ResourceLocation custom = SkinTextureHelper.customSkin(player.getUUID());
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getCloakTextureLocation", at = @At("RETURN"), cancellable = true)
    private void wskinloader$customCape(CallbackInfoReturnable<ResourceLocation> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        ResourceLocation custom = SkinTextureHelper.customCape(player.getUUID());
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "getModelName", at = @At("RETURN"), cancellable = true)
    private void wskinloader$customModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        String resolved = SkinTextureHelper.resolveModelName(player.getUUID(), cir.getReturnValue());
        if (resolved != null && !resolved.equals(cir.getReturnValue())) {
            cir.setReturnValue(resolved);
        }
    }
}
