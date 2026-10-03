package com.wupeng.wskinloader.client.mixin;

import com.wupeng.wskinloader.client.SuppressedHeadState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips one face draw when the tab list has already decided the server rendered
 * that head itself (see {@link PlayerListHudMixin}).
 *
 * <p>Only the texture overload is intercepted: it is the one the tab list uses,
 * and matching on the texture keeps every other caller untouched.
 */
@Mixin(PlayerFaceExtractor.class)
public abstract class PlayerFaceExtractorMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;IIIZZI)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void wskinloader$skipDuplicateHead(GuiGraphicsExtractor graphics, Identifier texture, int x, int y,
                                                      int size, boolean hat, boolean flip, int color,
                                                      CallbackInfo ci) {
        if (SuppressedHeadState.isSuppressed(texture)) {
            SuppressedHeadState.clear();
            ci.cancel();
        }
    }
}
