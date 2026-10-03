package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让 Tab 列表中的玩家头像在离线 / 第三方服务器上也显示。
 *
 * <p>原版逻辑：只有在本地服务器或加密连接时才显示玩家头像。修改后可通过
 * {@code enableTabListHeads} 开关控制。
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;isLocalServer()Z"
        )
    )
    private boolean wskinloader$alwaysShowPlayerHeads(Minecraft instance) {
        ModConfig config = ModConfig.getInstance();
        if (!config.enableTabListHeads) {
            return instance.isLocalServer();
        }
        return true;
    }
}
