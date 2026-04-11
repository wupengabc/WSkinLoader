package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 PlayerInfo 的皮肤获取，让 Tab 列表也能显示自定义皮肤
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerListEntryMixin {
    
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerInfo entry = (PlayerInfo) (Object) this;
        PlayerSkin modified = SkinTextureHelper.createModifiedSkin(entry.getProfile().id(), cir.getReturnValue());
        
        if (modified != null) {
            cir.setReturnValue(modified);
        }
    }
}
