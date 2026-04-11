package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.blockentity.state.SkullBlockRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截头颅渲染状态提取，触发自定义皮肤加载
 * 不直接修改 RenderType，让 PlayerSkinRenderCache 正常工作
 * 这样 3D Skin Layers 等模组也能正常获取皮肤数据
 */
@Mixin(SkullBlockRenderer.class)
public abstract class SkullBlockEntityRendererMixin {
    
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void onExtractRenderState(SkullBlockEntity blockEntity, SkullBlockRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress, CallbackInfo ci) {
        if (blockEntity == null) {
            return;
        }
        
        ResolvableProfile ownerProfile = blockEntity.getOwnerProfile();
        if (ownerProfile == null || ownerProfile.partialProfile() == null || ownerProfile.partialProfile().id() == null) {
            return;
        }
        
        // 如果还没有缓存，触发皮肤加载
        if (SkinCache.getSkin(ownerProfile.partialProfile().id()) == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSkinManager() != null) {
                PlayerSkinProviderAccessor accessor = (PlayerSkinProviderAccessor) client.getSkinManager();
                SkinLoader.loadSkinForProfile(ownerProfile.partialProfile(), accessor.getDownloader());
            }
        }
    }
}
