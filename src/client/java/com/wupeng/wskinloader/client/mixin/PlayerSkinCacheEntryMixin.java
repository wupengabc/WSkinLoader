package com.wupeng.wskinloader.client.mixin;

import com.mojang.authlib.GameProfile;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让所有经 {@code PlayerSkinRenderCache.RenderInfo} 绘制的皮肤头像使用自定义皮肤。
 *
 * <p>覆盖到的渲染路径：
 * <ul>
 *   <li>聊天/界面的 {@code player} 对象组件 —— {@code PlayerGlyphProvider} 用
 *       {@code glyphRenderTypes()} + {@code textureView()}</li>
 *   <li>物品栏与世界中的玩家头颅 —— {@code renderType()}</li>
 *   <li>直接调用 {@code playerSkin()} 的地方</li>
 * </ul>
 *
 * <p>关键点：{@code renderType()}/{@code textureView()}/{@code glyphRenderTypes()}
 * 三个 getter 读的是 <b>字段</b> {@code playerSkin}，不是 {@code playerSkin()} 方法，
 * 所以只注入方法无法影响聊天头像。这三个值又都是惰性记忆化的，第一次取值时
 * 自定义皮肤往往还没下载完，原版纹理就会被永久缓存。因此这里统一在
 * {@link #wskinloader$patchSkin} 中解析自定义皮肤，并清空记忆化字段，
 * 让它们从新皮肤重新派生。
 */
@Mixin(targets = "net.minecraft.client.renderer.PlayerSkinRenderCache$RenderInfo")
public abstract class PlayerSkinCacheEntryMixin {

    @Shadow @Final private GameProfile gameProfile;
    @Shadow @Final private PlayerSkin playerSkin;

    /** 记忆化字段，非 final，需要时可清空以强制重新派生。 */
    @Shadow private RenderType itemRenderType;
    @Shadow private com.mojang.renderpearl.api.textures.GpuTextureView textureView;
    @Shadow private GlyphRenderTypes glyphRenderTypes;

    /**
     * 当前记忆化字段所基于的自定义皮肤。
     * 缓存失效会产生新的 {@code ClientAsset.Texture} 实例，
     * 因此引用比较即可判断是否需要重建。
     */
    @Unique private ClientAsset.Texture wskinloader$memorizedSkin;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(PlayerSkinRenderCache outer, GameProfile gameProfile, PlayerSkin playerSkin,
                        PlayerSkin.Patch patch, CallbackInfo ci) {
        if (gameProfile == null || gameProfile.id() == null) {
            return;
        }
        if (!ModConfig.getInstance().enableChatFaces) {
            return;
        }

        // 缓存里已有皮肤时由 getter 注入负责替换；没有则触发一次加载。
        if (SkinCache.getSkin(gameProfile.id()) == null) {
            Minecraft client = Minecraft.getInstance();
            if (client != null && client.getSkinManager() != null) {
                PlayerSkinProviderAccessor accessor = (PlayerSkinProviderAccessor) client.getSkinManager();
                SkinLoader.loadSkinForProfile(gameProfile, accessor.getDownloader());
            }
        }
    }

    @Inject(method = "playerSkin", at = @At("RETURN"), cancellable = true)
    private void onGetPlayerSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin patched = wskinloader$patchSkin(cir.getReturnValue());
        if (patched != null) {
            cir.setReturnValue(patched);
        }
    }

    @Inject(method = "renderType", at = @At("HEAD"), cancellable = true)
    private void onRenderType(CallbackInfoReturnable<RenderType> cir) {
        if (wskinloader$resolveCustomSkin() != null && wskinloader$refreshMemoizedSkin()) {
            this.itemRenderType = SkullBlockRenderer.getPlayerSkinRenderType(this.wskinloader$memorizedSkin.texturePath());
        }
    }

    /**
     * 聊天头像走这里。原版会用字段 {@code playerSkin} 派生纹理类型，
     * 必须替换成自定义皮肤的纹理路径，否则聊天里的头像还是原版皮肤。
     */
    @Inject(method = "glyphRenderTypes", at = @At("HEAD"), cancellable = true)
    private void onGlyphRenderTypes(CallbackInfoReturnable<GlyphRenderTypes> cir) {
        if (wskinloader$resolveCustomSkin() != null && wskinloader$refreshMemoizedSkin()) {
            this.glyphRenderTypes = GlyphRenderTypes.createForColorTexture(this.wskinloader$memorizedSkin.texturePath());
        }
    }

    /**
     * 聊天头像的纹理句柄。同样必须指向自定义皮肤，否则字形会采样原版纹理，
     * 出现"渲染类型对了但贴图没变"的情况。
     */
    @Inject(method = "textureView", at = @At("HEAD"), cancellable = true)
    private void onTextureView(CallbackInfoReturnable<com.mojang.renderpearl.api.textures.GpuTextureView> cir) {
        if (wskinloader$resolveCustomSkin() != null && wskinloader$refreshMemoizedSkin()) {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                this.textureView = client.getTextureManager()
                        .getTexture(this.wskinloader$memorizedSkin.texturePath())
                        .getTextureView();
            }
        }
    }

    /**
     * 让三个记忆化字段基于当前自定义皮肤重建（仅当皮肤实例变化时）。
     *
     * @return true 表示 {@code wskinloader$memorizedSkin} 已就绪
     */
    @Unique
    private boolean wskinloader$refreshMemoizedSkin() {
        ClientAsset.Texture skin = wskinloader$resolveCustomSkin();
        if (skin == null) {
            // 自定义皮肤被清掉：作废派生值，让原版逻辑按字段里的原版皮肤重新派生。
            if (this.wskinloader$memorizedSkin != null) {
                this.wskinloader$memorizedSkin = null;
                this.itemRenderType = null;
                this.textureView = null;
                this.glyphRenderTypes = null;
            }
            return false;
        }
        if (skin == this.wskinloader$memorizedSkin) {
            return true;
        }
        this.wskinloader$memorizedSkin = skin;
        // 皮肤变了，之前按旧皮肤算出的派生值全部作废，下次访问时重建。
        this.itemRenderType = null;
        this.textureView = null;
        this.glyphRenderTypes = null;
        return true;
    }

    /**
     * 构造替换后的 {@link PlayerSkin}，并清空依赖皮肤的记忆化字段。
     *
     * @return 需要替换时返回新皮肤；无需替换时返回 {@code null}
     */
    @Unique
    private PlayerSkin wskinloader$patchSkin(PlayerSkin original) {
        if (original == null || !ModConfig.getInstance().enableChatFaces) {
            return null;
        }
        ClientAsset.Texture skin = wskinloader$resolveCustomSkin();
        ClientAsset.Texture cape = wskinloader$resolveCustomCape();
        if (skin == null && cape == null) {
            return null;
        }

        PlayerSkin patched = new PlayerSkin(
                skin != null ? skin : original.body(),
                cape != null ? cape : original.cape(),
                original.elytra(),
                original.model(),
                original.secure()
        );
        if (patched.equals(original)) {
            return null;
        }
        wskinloader$refreshMemoizedSkin();
        return patched;
    }

    @Unique
    private ClientAsset.Texture wskinloader$resolveCustomSkin() {
        if (!ModConfig.getInstance().enableChatFaces) {
            return null;
        }
        return this.gameProfile != null && this.gameProfile.id() != null
                ? SkinCache.getSkin(this.gameProfile.id())
                : null;
    }

    @Unique
    private ClientAsset.Texture wskinloader$resolveCustomCape() {
        if (!ModConfig.getInstance().enableChatFaces) {
            return null;
        }
        return this.gameProfile != null && this.gameProfile.id() != null
                ? SkinCache.getCape(this.gameProfile.id())
                : null;
    }
}
