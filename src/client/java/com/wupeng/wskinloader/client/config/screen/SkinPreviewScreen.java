package com.wupeng.wskinloader.client.config.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Skin preview screen (Minecraft 1.16.5).
 *
 * <p>Renders the player model with the custom skin/cape from {@link SkinCache},
 * with drag-to-rotate and scroll-to-zoom. The custom textures are injected into
 * the cache under the rendered entity's UUID for the duration of the frame, so
 * the {@code AbstractClientPlayer} mixin picks them up without any extra hooks.
 */
public class SkinPreviewScreen extends Screen {

    private final Screen parent;
    private final String playerName;
    private final UUID playerUuid;
    private final ResourceLocation skinTexture;

    private float rotationX = 0F;
    private boolean dragging = false;
    private int modelSize = 70;

    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public SkinPreviewScreen(Screen parent, String playerName, UUID playerUuid, ResourceLocation skinTexture) {
        super(new TranslatableComponent("wskinloader.preview.title", playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.skinTexture = skinTexture;
    }

    @Override
    protected void init() {
        this.addButton(new Button(this.width / 2 - 60, this.height - 28, 120, 20,
                new TranslatableComponent("wskinloader.preview.button.close"), b -> this.goToParent()));

        this.previewX = this.width / 2 + 10;
        this.previewY = 40;
        this.previewW = Math.max(120, this.width / 2 - 30);
        this.previewH = this.height - 80;
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float delta) {
        this.renderBackground(stack);

        drawCenteredString(stack, this.font, this.title.getString(), this.width / 2, 12, 0xFFFFFFFF);

        int infoX = 20;
        int infoY = 40;
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.info_title"), infoX, infoY);
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.name")
                .append(new TextComponent(" " + this.playerName)), infoX, infoY);
        if (this.playerUuid != null) {
            infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.uuid")
                    .append(new TextComponent(" " + this.playerUuid)), infoX, infoY);
        }
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.skin")
                .append(new TranslatableComponent(
                        this.skinTexture != null || (this.playerUuid != null && SkinCache.getSkin(this.playerUuid) != null)
                                ? "wskinloader.preview.skin_loaded" : "wskinloader.preview.skin_not_loaded")), infoX, infoY);
        infoY += 8;
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.controls_title"), infoX, infoY);
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.controls.drag"), infoX, infoY);
        infoY = this.infoLine(stack, new TranslatableComponent("wskinloader.preview.controls.zoom"), infoX, infoY);
        this.infoLine(stack, new TranslatableComponent("wskinloader.preview.controls.esc"), infoX, infoY);

        this.renderPreview(stack);

        super.render(stack, mouseX, mouseY, delta);
    }

    private int infoLine(PoseStack stack, Component text, int x, int y) {
        drawString(stack, this.font, text.getString(), x, y, 0xFFC8C8D2);
        return y + 12;
    }

    private void renderPreview(PoseStack stack) {
        int centerX = this.previewX + this.previewW / 2;
        int centerY = this.previewY + this.previewH / 2;

        if (this.minecraft == null || this.minecraft.player == null) {
            drawCenteredString(stack, this.font,
                    new TranslatableComponent("wskinloader.preview.error.need_world").getString(),
                    centerX, centerY, 0xFFFF7777);
            return;
        }

        LivingEntity entity = this.findPlayerEntity();
        if (entity == null) {
            entity = this.minecraft.player;
        }

        UUID id = entity.getUUID();
        ResourceLocation savedSkin = SkinCache.getSkin(id);
        ResourceLocation savedCape = SkinCache.getCape(id);
        String savedModel = SkinCache.getModel(id);

        ResourceLocation skinToUse = this.skinTexture;
        if (skinToUse == null && this.playerUuid != null) {
            skinToUse = SkinCache.getSkin(this.playerUuid);
        }
        ResourceLocation capeToUse = this.playerUuid != null ? SkinCache.getCape(this.playerUuid) : null;
        String modelToUse = this.playerUuid != null ? SkinCache.getModel(this.playerUuid) : null;

        try {
            if (skinToUse != null) {
                SkinCache.cacheSkin(id, skinToUse);
            }
            if (capeToUse != null) {
                SkinCache.cacheCape(id, capeToUse);
            }
            if (modelToUse != null) {
                SkinCache.cacheModel(id, modelToUse);
            }
            this.drawEntity(centerX, centerY, this.modelSize, this.rotationX, entity);
        } catch (Exception e) {
            drawCenteredString(stack, this.font,
                    new TranslatableComponent("wskinloader.preview.error.render_failed").getString(),
                    centerX, centerY, 0xFFFF7777);
        } finally {
            if (savedSkin != null) {
                SkinCache.cacheSkin(id, savedSkin);
            } else {
                SkinCache.removeSkin(id);
            }
            if (savedCape != null) {
                SkinCache.cacheCape(id, savedCape);
            } else {
                SkinCache.removeCape(id);
            }
            if (savedModel != null) {
                SkinCache.cacheModel(id, savedModel);
            } else {
                SkinCache.removeModel(id);
            }
        }
    }

    /**
     * Renders {@code entity} centered at (x, y), modelled on
     * {@code InventoryScreen.renderEntityInInventory}.
     */
    private void drawEntity(int x, int y, int size, float yaw, LivingEntity entity) {
        float f = (float) Math.atan(-yaw / 40.0F);
        RenderSystem.pushMatrix();
        RenderSystem.translatef(x, y, 1050.0F);
        RenderSystem.scalef(1.0F, 1.0F, -1.0F);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0, 0.0, 1000.0);
        poseStack.scale((float) size, (float) size, (float) size);
        Quaternion base = Vector3f.ZP.rotationDegrees(180.0F);
        Quaternion tilt = Vector3f.XP.rotationDegrees(0.0F);
        base.mul(tilt);
        poseStack.mulPose(base);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, yaw, 1.0F, poseStack, buffer, 15728880));
        buffer.endBatch();
        dispatcher.setRenderShadow(true);
        RenderSystem.popMatrix();
    }

    private LivingEntity findPlayerEntity() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        for (AbstractClientPlayer player : this.minecraft.level.players()) {
            if (this.playerUuid != null && player.getUUID().equals(this.playerUuid)) {
                return player;
            }
            if (this.playerName != null && player.getName().getString().equals(this.playerName)) {
                return player;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        this.modelSize = Math.max(30, Math.min(120, this.modelSize + (int) (amount * 5)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging) {
            this.rotationX += (float) deltaX * 1.5F;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.goToParent();
    }

    private void goToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
