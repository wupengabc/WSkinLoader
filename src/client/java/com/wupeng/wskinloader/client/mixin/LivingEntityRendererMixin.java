package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.PlayerSkinSourceCache;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在玩家名称标签旁显示皮肤来源
 */
@Mixin(EntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends Entity, S extends EntityRenderState> {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("wskinloader");
    private static boolean debugLogged = false;
    
    @Inject(
        method = "extractRenderState",
        at = @At("TAIL")
    )
    private void onExtractRenderState(
        T entity,
        S state,
        float partialTicks,
        CallbackInfo ci
    ) {
        // 只处理玩家实体
        if (!(entity instanceof Player player)) {
            return;
        }
        
        // 检查配置是否启用
        ModConfig config = ModConfig.getInstance();
        if (!config.enableNameTagLabel) {
            if (!debugLogged) {
                LOGGER.info("[WSkinLoader] Name tag label is disabled in config");
                debugLogged = true;
            }
            return;
        }
        
        // 检查是否有名称标签
        if (state.nameTag == null) {
            return;
        }
        
        // 获取皮肤来源
        String source = PlayerSkinSourceCache.getSource(player.getUUID());
        if (source == null || source.isEmpty()) {
            if (!debugLogged) {
                LOGGER.info("[WSkinLoader] No source found for player: {} (UUID: {})", 
                    player.getName().getString(), player.getUUID());
                debugLogged = true;
            }
            return;
        }
        
        // 在名称标签后添加来源信息
        Component originalName = state.nameTag;
        Component sourceText = Component.literal(" [" + source + "]").withColor(0x4677FF); // 蓝色
        state.nameTag = originalName.copy().append(sourceText);
        
        if (!debugLogged) {
            LOGGER.info("[WSkinLoader] Added source label '{}' to player: {}", 
                source, player.getName().getString());
            debugLogged = true;
        }
    }
}
