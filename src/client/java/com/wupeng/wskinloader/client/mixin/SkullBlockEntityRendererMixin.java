package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渲染玩家头颅时触发自定义皮肤加载。
 *
 * <p>This trigger does not replace vanilla skull rendering's skin lookup.
 */
@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockEntityRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/level/block/entity/SkullBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("HEAD")
    )
    private void wskinloader$loadSkullSkin(SkullBlockEntity blockEntity, float partialTicks, PoseStack poseStack,
                                           MultiBufferSource buffer, int combinedLight, int combinedOverlay,
                                           CallbackInfo ci) {
        if (blockEntity == null) {
            return;
        }
        ResolvableProfile profile = blockEntity.getOwnerProfile();
        GameProfile owner = profile == null ? null : profile.gameProfile();
        if (owner == null || owner.getId() == null) {
            return;
        }
        SkinLoader.loadSkinForProfile(owner);
    }
}
