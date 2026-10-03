package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.PlayerSkinSourceCache;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 在玩家名称标签旁显示皮肤来源。
 *
 * <p>重定向 {@code EntityRenderer.render} 里读取显示名的调用点，把来源标签追加到
 * 名称组件后面（原版名称标签用同一个组件渲染）。
 */
@Mixin(EntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getDisplayName()Lnet/minecraft/network/chat/Component;"
        )
    )
    private Component wskinloader$appendSourceLabel(Entity entity) {
        Component original = entity.getDisplayName();
        if (!(entity instanceof Player) || !ModConfig.getInstance().enableNameTagLabel) {
            return original;
        }
        String source = PlayerSkinSourceCache.getSource(entity.getUUID());
        if (source == null || source.isEmpty()) {
            return original;
        }
        return original.copy().append(new TextComponent(" [" + source + "]").withStyle(ChatFormatting.BLUE));
    }
}
