package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.skin.SkinLoader;
import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 用 WSkinLoader 解析出的自定义皮肤 / 披风替换客户端玩家的纹理。
 *
 * <p>The aggregate getter also triggers asynchronous loading before applying overrides.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;", at = @At("HEAD"))
    private void wskinloader$ensureLoaded(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        String name = player.getGameProfile().getName();
        if (name != null && !name.isEmpty()) {
            SkinLoader.loadSkinForProfile(player.getGameProfile());
        }
    }

    @Inject(method = "getSkin()Lnet/minecraft/client/resources/PlayerSkin;", at = @At("RETURN"), cancellable = true)
    private void wskinloader$customSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        PlayerSkin resolved = SkinTextureHelper.resolvePlayerSkin(player.getUUID(), cir.getReturnValue());
        if (resolved != cir.getReturnValue()) {
            cir.setReturnValue(resolved);
        }
    }
}
