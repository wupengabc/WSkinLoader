package com.wupeng.wskinloader.client.config.screen;

import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Skin preview screen.
 *
 * Layout, header and footer use vanilla widgets (HeaderAndFooterLayout,
 * StringWidget, Button, CycleButton) so UI add-ons and resource packs can
 * restyle it like any vanilla screen. Only the 3D viewport itself is rendered
 * directly, which is inherent to rendering a live entity model.
 */
public class SkinPreviewScreen extends Screen {

    private static final int PANEL_TOP_MARGIN = 10;
    private static final int PANEL_BOTTOM_MARGIN = 8;
    private static final int PREVIEW_INSET = 16;

    private final Screen parent;
    private final String playerName;
    private final UUID playerUuid;
    private final ClientAsset.Texture skinTexture;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final List<AbstractWidget> infoWidgets = new ArrayList<>();

    private StringWidget zoomWidget;
    private CycleButton<Boolean> modelButton;

    private float rotationX = 180;
    private boolean dragging = false;
    private int modelSize = 70;
    private boolean forceSlim = false;

    // Viewport rectangle (also used for drag hit testing)
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public SkinPreviewScreen(Screen parent, String playerName, UUID playerUuid, ClientAsset.Texture skinTexture) {
        super(Component.translatable("wskinloader.preview.title", playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.skinTexture = skinTexture;
    }

    @Override
    protected void init() {
        this.infoWidgets.clear();

        this.layout.addTitleHeader(this.title, this.font);

        this.addInfoLine(Component.translatable("wskinloader.preview.info_title"));
        this.addInfoLine(Component.translatable("wskinloader.preview.name")
                .copy().append(Component.literal(" " + this.playerName)));
        if (this.playerUuid != null) {
            this.addInfoLine(Component.translatable("wskinloader.preview.uuid")
                    .copy().append(Component.literal(" " + this.playerUuid)));
        }
        this.addInfoLine(Component.translatable("wskinloader.preview.skin")
                .copy().append(Component.literal(" ").append(Component.translatable(
                        this.skinTexture != null || (this.playerUuid != null && SkinCache.getSkin(this.playerUuid) != null)
                                ? "wskinloader.preview.skin_loaded"
                                : "wskinloader.preview.skin_not_loaded"))));

        this.addInfoLine(Component.translatable("wskinloader.preview.controls_title"));
        this.addInfoLine(Component.translatable("wskinloader.preview.controls.drag"));
        this.addInfoLine(Component.translatable("wskinloader.preview.controls.zoom"));
        this.addInfoLine(Component.translatable("wskinloader.preview.controls.esc"));

        this.zoomWidget = new StringWidget(220, 12,
                Component.translatable("wskinloader.preview.zoom", this.modelSize), this.font);
        this.addInfoWidget(this.zoomWidget);

        this.modelButton = new CycleButton.Builder<Boolean>(
                value -> Component.translatable(value
                        ? "wskinloader.preview.model_slim"
                        : "wskinloader.preview.model_wide"),
                () -> this.forceSlim)
                .withValues(List.of(false, true))
                .create(0, 0, 180, 20, Component.translatable("wskinloader.preview.model_type"),
                        (button, value) -> this.forceSlim = value);
        this.addInfoWidget(this.modelButton);

        this.layout.addToFooter(Button.builder(Component.translatable("wskinloader.preview.button.close"),
                button -> this.goToParent()).width(120).build());

        this.layout.visitWidgets(this::addRenderableWidget);

        this.repositionElements();
    }

    private void addInfoLine(Component text) {
        this.addInfoWidget(new StringWidget(240, 12, text, this.font));
    }

    private void addInfoWidget(AbstractWidget widget) {
        this.infoWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();

        int contentTop = this.layout.getHeaderHeight() + PANEL_TOP_MARGIN;
        int contentBottom = this.height - this.layout.getFooterHeight() - PANEL_BOTTOM_MARGIN;
        int totalWidth = Math.min(760, this.width - 40);
        int left = (this.width - totalWidth) / 2;
        int halfWidth = (totalWidth - 12) / 2;

        // Left: info column
        int infoX = left + 12;
        int infoY = contentTop + 10;
        for (AbstractWidget widget : this.infoWidgets) {
            widget.setPosition(infoX, infoY);
            infoY += widget.getHeight() + 4;
        }

        // Right: 3D viewport
        this.previewX = left + halfWidth + 12;
        this.previewY = contentTop;
        this.previewW = halfWidth;
        this.previewH = contentBottom - contentTop;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Viewport is intentionally transparent so the (blurred) world shows through.
        ctx.centeredText(this.font, Component.translatable("wskinloader.preview.3d_title"),
                this.previewX + this.previewW / 2, this.previewY + 8, 0xFFA0A0B0);
        this.renderPreview(ctx, mouseX, mouseY);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int centerX = this.previewX + this.previewW / 2;
        int centerY = this.previewY + this.previewH / 2;

        if (this.minecraft == null || this.minecraft.player == null) {
            graphics.centeredText(this.font, Component.translatable("wskinloader.preview.error.need_world"),
                    centerX, centerY, 0xFFFF7777);
            return;
        }

        LivingEntity entity = this.findPlayerEntity();
        if (entity == null) {
            entity = this.minecraft.player;
        }

        ClientAsset.Texture skinToUse = this.skinTexture;
        if (skinToUse == null && this.playerUuid != null) {
            skinToUse = SkinCache.getSkin(this.playerUuid);
        }
        ClientAsset.Texture capeToUse = this.playerUuid != null ? SkinCache.getCape(this.playerUuid) : null;

        boolean usingCurrentPlayerAsBase = this.playerUuid != null && !entity.getUUID().equals(this.playerUuid);

        int x0 = this.previewX + PREVIEW_INSET;
        int y0 = this.previewY + 22;
        int x1 = this.previewX + this.previewW - PREVIEW_INSET;
        int y1 = this.previewY + this.previewH - PREVIEW_INSET;

        try {
            graphics.enableScissor(x0, y0, x1, y1);
            this.extractEntityWithCustomTextures(graphics, x0, y0, x1, y1, this.modelSize, 0.0625F,
                    this.rotationX, 0F, entity, skinToUse, capeToUse, usingCurrentPlayerAsBase);
            graphics.disableScissor();
        } catch (Exception e) {
            graphics.centeredText(this.font, Component.translatable("wskinloader.preview.error.render_failed"),
                    centerX, centerY, 0xFFFF7777);
        }
    }

    private LivingEntity findPlayerEntity() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        for (net.minecraft.client.player.AbstractClientPlayer player : this.minecraft.level.players()) {
            if (this.playerUuid != null && player.getUUID().equals(this.playerUuid)) {
                return player;
            }
            if (this.playerName != null && player.getName().getString().equals(this.playerName)) {
                return player;
            }
        }
        return null;
    }

    private void extractEntityWithCustomTextures(
            GuiGraphicsExtractor graphics,
            int x0, int y0, int x1, int y1,
            int size, float offsetY,
            float rotX, float rotY,
            LivingEntity entity,
            ClientAsset.Texture customSkin,
            ClientAsset.Texture customCape,
            boolean stripEntityCape) {

        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateY(rotX * (float) (Math.PI / 180.0));
        Quaternionf xRotation = new Quaternionf();

        EntityRenderState renderState = extractRenderState(entity);

        if (renderState instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState) {
            net.minecraft.world.entity.player.PlayerSkin currentSkin = avatarState.skin;

            net.minecraft.world.entity.player.PlayerModelType modelType;
            if (this.modelButton != null) {
                modelType = this.forceSlim
                        ? net.minecraft.world.entity.player.PlayerModelType.SLIM
                        : net.minecraft.world.entity.player.PlayerModelType.WIDE;
            } else {
                modelType = currentSkin.model();
            }

            ClientAsset.Texture capeToRender = customCape;
            if (capeToRender == null && !stripEntityCape) {
                capeToRender = currentSkin.cape();
            }

            avatarState.skin = new net.minecraft.world.entity.player.PlayerSkin(
                    customSkin != null ? customSkin : currentSkin.body(),
                    capeToRender,
                    currentSkin.elytra(),
                    modelType,
                    currentSkin.secure()
            );
        }

        applyPoseToRenderState(renderState);

        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + offsetY, 0.0F);
        graphics.entity(renderState, size, translation, rotation, xRotation, x0, y0, x1, y1);
    }

    private static void applyPoseToRenderState(EntityRenderState renderState) {
        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyRot = 0;
            livingState.yRot = 0;
            livingState.xRot = 0;

            livingState.wornHeadType = null;
            livingState.wornHeadProfile = null;
            livingState.wornHeadAnimationPos = 0;

            livingState.isFullyFrozen = false;
            livingState.hasRedOverlay = false;
            livingState.isAutoSpinAttack = false;
            livingState.isInWater = false;
            livingState.deathTime = 0;
            livingState.walkAnimationPos = 0;
            livingState.walkAnimationSpeed = 0;

            if (livingState instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoidState) {
                humanoidState.headEquipment = ItemStack.EMPTY;
                humanoidState.chestEquipment = ItemStack.EMPTY;
                humanoidState.legsEquipment = ItemStack.EMPTY;
                humanoidState.feetEquipment = ItemStack.EMPTY;
                humanoidState.isCrouching = false;
                humanoidState.isFallFlying = false;
                humanoidState.isVisuallySwimming = false;
                humanoidState.isPassenger = false;
                humanoidState.isUsingItem = false;
            }

            if (livingState instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armedState) {
                armedState.rightHandItemStack = ItemStack.EMPTY;
                armedState.leftHandItemStack = ItemStack.EMPTY;
                armedState.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
                armedState.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
            }

            if (livingState instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState) {
                avatarState.arrowCount = 0;
                avatarState.stingerCount = 0;
                avatarState.parrotOnLeftShoulder = null;
                avatarState.parrotOnRightShoulder = null;
                avatarState.isSpectator = false;
            }

            livingState.boundingBoxWidth = livingState.boundingBoxWidth / livingState.scale;
            livingState.boundingBoxHeight = livingState.boundingBoxHeight / livingState.scale;
            livingState.scale = 1.0F;
        }
    }

    private static EntityRenderState extractRenderState(LivingEntity entity) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super LivingEntity, ?> renderer = dispatcher.getRenderer(entity);
        EntityRenderState renderState = renderer.createRenderState(entity, 1.0F);
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;
        return renderState;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.modelSize = Math.max(30, Math.min(90, this.modelSize + (int) (verticalAmount * 5)));
        if (this.zoomWidget != null) {
            this.zoomWidget.setMessage(Component.translatable("wskinloader.preview.zoom", this.modelSize));
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Vanilla widgets (model type, close) get the click first; a press that
        // no widget claims starts a rotation drag.
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        // Minecraft 26.3 uses SDL button numbering: primary mouse button is 1.
        if (event.button() == 1) {
            this.dragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragging) {
            this.rotationX += (float) deltaX * 1.5F;
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1) {
            this.dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        this.goToParent();
    }

    private void goToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }
}
