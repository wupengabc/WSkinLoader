package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Tab 列表中的玩家头像始终显示
 * 原版逻辑：只有在本地服务器或加密连接时才显示玩家头像
 * 修改后：可通过配置开关控制
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {
    
    @Redirect(
        method = "extractRenderState",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"
        )
    )
    private boolean alwaysShowPlayerHeads(ClientPacketListener instance) {
        ModConfig config = ModConfig.getInstance();
        if (config.enableTabListHeads) {
            return true;
        }
        return instance.onlineMode();
    }
}
