package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 渲染玩家头颅时触发自定义皮肤加载。
 *
 * <p>1.16.5 的 {@code SkullBlockRenderer} 会直接查询 {@code SkinManager} 的
 * 不安全皮肤信息；提前把自定义皮肤注册好，头颅就能复用自定义皮肤。
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
        GameProfile owner = blockEntity.getOwnerProfile();
        if (owner == null || owner.getId() == null) {
            return;
        }
        SkinLoader.loadSkinForProfile(owner);
    }
}
