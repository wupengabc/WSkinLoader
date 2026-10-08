package com.wupeng.wskinloader.client.config.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Skin preview screen (Minecraft 1.20.6).
 *
 * <p>Renders the player model with the custom skin/cape from {@link SkinCache},
 * with drag-to-rotate and scroll-to-zoom. The custom textures are injected into
 * the cache under the rendered entity's UUID for the duration of the frame.
 * A scoped cape override also handles targets without a cape.
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
        super(Component.translatable("wskinloader.preview.title", playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.skinTexture = skinTexture;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("wskinloader.preview.button.close"),
                b -> this.goToParent()).bounds(this.width / 2 - 60, this.height - 28, 120, 20).build());

        this.previewX = this.width / 2 + 10;
        this.previewY = 40;
        this.previewW = Math.max(120, this.width / 2 - 30);
        this.previewH = this.height - 80;
    }

    @Override
    public void render(GuiGraphics stack, int mouseX, int mouseY, float delta) {
        super.render(stack, mouseX, mouseY, delta);

        stack.drawCenteredString(this.font, this.title.getString(), this.width / 2, 12, 0xFFFFFFFF);

        int infoX = 20;
        int infoY = 40;
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.info_title"), infoX, infoY);
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.name")
                .append(Component.literal(" " + this.playerName)), infoX, infoY);
        if (this.playerUuid != null) {
            infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.uuid")
                    .append(Component.literal(" " + this.playerUuid)), infoX, infoY);
        }
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.skin")
                .append(Component.translatable(
                        this.skinTexture != null || (this.playerUuid != null && SkinCache.getCachedSkin(this.playerUuid) != null)
                                ? "wskinloader.preview.skin_loaded" : "wskinloader.preview.skin_not_loaded")), infoX, infoY);
        infoY += 8;
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.controls_title"), infoX, infoY);
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.controls.drag"), infoX, infoY);
        infoY = this.infoLine(stack, Component.translatable("wskinloader.preview.controls.zoom"), infoX, infoY);
        this.infoLine(stack, Component.translatable("wskinloader.preview.controls.esc"), infoX, infoY);

        this.renderPreview(stack);

    }

    private int infoLine(GuiGraphics stack, Component text, int x, int y) {
        stack.drawString(this.font, text.getString(), x, y, 0xFFC8C8D2);
        return y + 12;
    }

    private void renderPreview(GuiGraphics stack) {
        int centerX = this.previewX + this.previewW / 2;
        int centerY = this.previewY + this.previewH / 2;

        if (this.minecraft == null || this.minecraft.player == null) {
            stack.drawCenteredString(this.font,
                    Component.translatable("wskinloader.preview.error.need_world").getString(),
                    centerX, centerY, 0xFFFF7777);
            return;
        }

        LivingEntity entity = this.findPlayerEntity();
        if (entity == null) {
            entity = this.minecraft.player;
        }

        UUID id = entity.getUUID();
        ResourceLocation savedSkin = SkinCache.getSkin(id);
        String savedModel = SkinCache.getModel(id);

        ResourceLocation skinToUse = this.playerUuid != null ? SkinCache.getCachedSkin(this.playerUuid) : null;
        if (skinToUse == null) {
            skinToUse = this.skinTexture;
        }
        ResourceLocation capeToUse = this.playerUuid != null ? SkinCache.getCachedCape(this.playerUuid) : null;
        String modelToUse = this.playerUuid != null ? SkinCache.getCachedModel(this.playerUuid) : null;

        SkinTextureHelper.PreviewCape savedPreviewCape = SkinTextureHelper.setPreviewCape(
                new SkinTextureHelper.PreviewCape(id, capeToUse));
        try {
            if (skinToUse != null) {
                SkinCache.cacheSkin(id, skinToUse);
            }
            if (modelToUse != null) {
                SkinCache.cacheModel(id, modelToUse);
            }
            this.drawEntity(stack, centerX, centerY, this.modelSize, this.rotationX, entity);
        } catch (Exception e) {
            stack.drawCenteredString(this.font,
                    Component.translatable("wskinloader.preview.error.render_failed").getString(),
                    centerX, centerY, 0xFFFF7777);
        } finally {
            SkinTextureHelper.setPreviewCape(savedPreviewCape);
            if (savedSkin != null) {
                SkinCache.cacheSkin(id, savedSkin);
            } else {
                SkinCache.removeSkin(id);
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
     *
     * <p>The renderer reads entity rotations, so they are temporarily replaced
     * and restored after rendering, as in the inventory screen.
     */
    private void drawEntity(GuiGraphics graphics, int x, int y, int size, float yawDegrees, LivingEntity entity) {
        graphics.flush();
        // A failed renderer can leave extra poses; isolate it from the screen's stack.
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(graphics.pose().last().pose());
        poseStack.last().normal().set(graphics.pose().last().normal());
        poseStack.pushPose();
        poseStack.translate(x, y, 50.0);
        poseStack.scale(size, size, -size);
        Quaternionf base = Axis.ZP.rotationDegrees(180.0F);
        Quaternionf tilt = new Quaternionf();
        base.mul(tilt);
        poseStack.mulPose(base);

        float prevBodyRot = entity.yBodyRot;
        float prevBodyRotO = entity.yBodyRotO;
        float prevYRot = entity.getYRot();
        float prevYRotO = entity.yRotO;
        float prevXRot = entity.getXRot();
        float prevXRotO = entity.xRotO;
        float prevHeadRot = entity.yHeadRot;
        float prevHeadRotO = entity.yHeadRotO;

        entity.yBodyRot = 180.0F + yawDegrees;
        entity.yBodyRotO = entity.yBodyRot;
        entity.setYRot(180.0F + yawDegrees);
        entity.yRotO = entity.getYRot();
        entity.setXRot(0.0F);
        entity.xRotO = 0.0F;
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        Quaternionf previousCamera = new Quaternionf(dispatcher.cameraOrientation());
        try {
            Lighting.setupForEntityInInventory();
            dispatcher.overrideCameraOrientation(new Quaternionf(tilt));
            dispatcher.setRenderShadow(false);
            MultiBufferSource.BufferSource buffer = graphics.bufferSource();
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, poseStack, buffer, 15728880));
            graphics.flush();
        } finally {
            dispatcher.setRenderShadow(true);
            dispatcher.overrideCameraOrientation(previousCamera);
            entity.yBodyRot = prevBodyRot;
            entity.yBodyRotO = prevBodyRotO;
            entity.setYRot(prevYRot);
            entity.yRotO = prevYRotO;
            entity.setXRot(prevXRot);
            entity.xRotO = prevXRotO;
            entity.yHeadRot = prevHeadRot;
            entity.yHeadRotO = prevHeadRotO;
            poseStack.popPose();
            Lighting.setupFor3DItems();
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
        this.modelSize = Math.max(30, Math.min(120, this.modelSize + (int) (amount * 5)));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // super() calls the Screen event handler, which handles the close button
        // but does not claim the drag; claim the left button on the preview area.
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
        // Minecraft routes drags to the screen only while a button is held; the
        // delta args are already the per-frame GUI movement.
        if (this.dragging && button == 0) {
            this.rotationX -= (float) deltaX * 1.5F;
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
