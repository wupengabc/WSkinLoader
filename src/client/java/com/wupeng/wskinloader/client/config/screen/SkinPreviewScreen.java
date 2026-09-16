package com.wupeng.wskinloader.client.config.screen;

import com.wupeng.wskinloader.client.skin.SkinCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 皮肤预览屏幕 - 3D模型预览
 * 
 * 主题：暗色仪表盘风格，与 ConfigScreen 一致。
 * 修复：预览其他玩家时不再携带当前玩家的盔甲。
 */
public class SkinPreviewScreen extends Screen {

    // ─── Colors (与 ConfigScreen 一致) ───
    private static final int BG_OVERLAY = 0xDD1A1A2E;
    private static final int BG_PANEL = 0xFF1E1E28;
    private static final int BG_TITLEBAR = 0xFF111118;
    private static final int BG_PREVIEW_BOX = 0xFF161620;
    private static final int BG_BUTTON = 0xFF2A2A38;
    private static final int BORDER = 0xFF2A2A38;
    private static final int ACCENT = 0xFF2BB673;
    private static final int TEXT_PRIMARY = 0xFFE0E0F0;
    private static final int TEXT_SECONDARY = 0xFF8A8A9A;
    private static final int TEXT_MUTED = 0xFF5A5A6A;
    private static final int TEXT_SUCCESS = 0xFF4CAF50;
    private static final int TEXT_ERROR = 0xFFE04848;
    private static final int SEPARATOR = 0xFF2A2A38;

    private final Screen parent;
    private final String playerName;
    private final UUID playerUuid;
    private final ClientAsset.Texture skinTexture;

    private float rotationX = 180; // 初始 180 度让模型正面朝向屏幕
    private float rotationY = 0; // 垂直旋转角度（累积）
    private boolean dragging = false;
    private double dragStartX, dragStartY;
    private float dragStartRotX, dragStartRotY;
    private int modelSize = 70;
    private boolean forceSlim = false;
    private boolean modelTypeOverride = false;
    private int mouseXPos, mouseYPos;
    private int modelBtnY; // Y position of model type buttons (computed during render)

    public SkinPreviewScreen(Screen parent, String playerName, UUID playerUuid, ClientAsset.Texture skinTexture) {
        super(Component.translatable("wskinloader.preview.title", playerName));
        this.parent = parent;
        this.playerName = playerName;
        this.playerUuid = playerUuid;
        this.skinTexture = skinTexture;
    }

    @Override
    protected void init() {
        // 关闭按钮在底部居中
        addRenderableWidget(
                Button.builder(
                        Component.translatable("wskinloader.preview.button.close"),
                        btn -> { if (minecraft != null) minecraft.setScreenAndShow(parent); }
                ).bounds(width / 2 - 60, height - 36, 120, 24).build()
        );
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.mouseXPos = mouseX;
        this.mouseYPos = mouseY;
        // 拖拽时更新旋转角度（只允许水平旋转）
        if (dragging) {
            rotationX = dragStartRotX + (float)(mouseX - dragStartX) * 1.5f;
        }

        // ── 1. 全屏暗色背景 ──
        context.fill(0, 0, width, height, BG_OVERLAY);

        // ── 2. 顶部标题栏 ──
        context.fill(0, 0, width, 36, BG_TITLEBAR);
        context.fill(0, 35, width, 36, SEPARATOR);
        context.centeredText(font, title, width / 2, 14, TEXT_PRIMARY);

        // ── 3. 左侧信息面板 ──
        int leftPanelX = 20;
        int leftPanelY = 50;
        int leftPanelW = width / 2 - 40;
        int leftPanelH = height - 100;

        // 面板背景
        context.fill(leftPanelX, leftPanelY, leftPanelX + leftPanelW, leftPanelY + leftPanelH, BG_PANEL);
        drawOutline(context, leftPanelX, leftPanelY, leftPanelW, leftPanelH, BORDER);

        // 使用 scissor 裁剪左侧面板内容，防止小窗口时溢出
        context.enableScissor(leftPanelX + 1, leftPanelY + 1, leftPanelX + leftPanelW - 1, leftPanelY + leftPanelH - 1);

        int infoX = leftPanelX + 12;
        int infoY = leftPanelY + 12;

        // 面板标题
        context.text(font, Component.translatable("wskinloader.preview.info_title"), infoX, infoY, ACCENT, false);
        infoY += 14;
        context.fill(infoX, infoY, leftPanelX + leftPanelW - 12, infoY + 1, SEPARATOR);
        infoY += 8;

        // 玩家名称
        context.text(font, Component.translatable("wskinloader.preview.name"), infoX, infoY, TEXT_SECONDARY, false);
        context.text(font, Component.literal(playerName), infoX + 45, infoY, TEXT_PRIMARY, false);
        infoY += 14;

        // UUID
        if (playerUuid != null) {
            context.text(font, Component.translatable("wskinloader.preview.uuid"), infoX, infoY, TEXT_SECONDARY, false);
            String uuidStr = playerUuid.toString();
            context.text(font, Component.literal(uuidStr.substring(0, 18)), infoX + 45, infoY, TEXT_MUTED, false);
            infoY += 10;
            context.text(font, Component.literal(uuidStr.substring(18)), infoX + 45, infoY, TEXT_MUTED, false);
            infoY += 14;
        }

        // 皮肤状态
        context.text(font, Component.translatable("wskinloader.preview.skin"), infoX, infoY, TEXT_SECONDARY, false);
        if (skinTexture != null) {
            context.text(font, Component.translatable("wskinloader.preview.skin_loaded"), infoX + 45, infoY, TEXT_SUCCESS, false);
        } else {
            context.text(font, Component.translatable("wskinloader.preview.skin_not_loaded"), infoX + 45, infoY, TEXT_ERROR, false);
        }
        infoY += 18;

        // 分隔线
        context.fill(infoX, infoY, leftPanelX + leftPanelW - 12, infoY + 1, SEPARATOR);
        infoY += 10;

        // 操作说明
        context.text(font, Component.translatable("wskinloader.preview.controls_title"), infoX, infoY, ACCENT, false);
        infoY += 14;
        context.text(font, Component.translatable("wskinloader.preview.controls.drag"), infoX + 4, infoY, TEXT_MUTED, false);
        infoY += 11;
        context.text(font, Component.translatable("wskinloader.preview.controls.zoom"), infoX + 4, infoY, TEXT_MUTED, false);
        infoY += 11;
        context.text(font, Component.translatable("wskinloader.preview.controls.esc"), infoX + 4, infoY, TEXT_MUTED, false);
        infoY += 16;
        // 缩放信息
        context.text(font, Component.translatable("wskinloader.preview.zoom", modelSize), infoX, infoY, TEXT_SECONDARY, false);
        infoY += 14;

        // 模型类型切换按钮（明显的可点击按钮样式）
        context.text(font, Component.translatable("wskinloader.preview.model_type"), infoX, infoY + 4, TEXT_SECONDARY, false);
        infoY += 16;
        this.modelBtnY = infoY; // 保存按钮 Y 位置供点击检测使用
        // 两个按钮：[宽体 WIDE] [纤细 SLIM]
        int btnW = (leftPanelW - 12 * 2 - 8) / 2; // 两个按钮平分宽度
        int wideX = infoX;
        int slimX = infoX + btnW + 8;
        int btnH = 18;
        boolean wideSelected = !forceSlim && modelTypeOverride || (!modelTypeOverride);
        boolean slimSelected = forceSlim && modelTypeOverride;
        // WIDE 按钮
        int wideBg = (!modelTypeOverride || !forceSlim) ? ACCENT : BG_BUTTON;
        int wideTextColor = (!modelTypeOverride || !forceSlim) ? 0xFFFFFFFF : TEXT_SECONDARY;
        boolean wideHover = mouseXPos >= wideX && mouseXPos < wideX + btnW && mouseYPos >= infoY && mouseYPos < infoY + btnH;
        context.fill(wideX, infoY, wideX + btnW, infoY + btnH, wideHover ? lighten(wideBg) : wideBg);
        drawOutline(context, wideX, infoY, btnW, btnH, BORDER);
        Component wideLabel = Component.translatable("wskinloader.preview.model_wide");
        int wideTextX = wideX + (btnW - font.width(wideLabel)) / 2;
        context.text(font, wideLabel, wideTextX, infoY + 5, wideTextColor, false);
        // SLIM 按钮
        int slimBg = (modelTypeOverride && forceSlim) ? ACCENT : BG_BUTTON;
        int slimTextColor = (modelTypeOverride && forceSlim) ? 0xFFFFFFFF : TEXT_SECONDARY;
        boolean slimHover = mouseXPos >= slimX && mouseXPos < slimX + btnW && mouseYPos >= infoY && mouseYPos < infoY + btnH;
        context.fill(slimX, infoY, slimX + btnW, infoY + btnH, slimHover ? lighten(slimBg) : slimBg);
        drawOutline(context, slimX, infoY, btnW, btnH, BORDER);
        Component slimLabel = Component.translatable("wskinloader.preview.model_slim");
        int slimTextX = slimX + (btnW - font.width(slimLabel)) / 2;
        context.text(font, slimLabel, slimTextX, infoY + 5, slimTextColor, false);

        // 结束左侧面板 scissor
        context.disableScissor();

        // ── 4. 右侧 3D 预览区域 ──
        int rightPanelX = width / 2 + 20;
        int rightPanelY = 50;
        int rightPanelW = width / 2 - 40;
        int rightPanelH = height - 100;

        // 预览框背景
        context.fill(rightPanelX, rightPanelY, rightPanelX + rightPanelW, rightPanelY + rightPanelH, BG_PREVIEW_BOX);
        drawOutline(context, rightPanelX, rightPanelY, rightPanelW, rightPanelH, BORDER);

        // 预览框标题
        context.centeredText(font, Component.translatable("wskinloader.preview.3d_title"), rightPanelX + rightPanelW / 2, rightPanelY + 8, TEXT_SECONDARY);

        // 渲染 3D 玩家模型
        int previewCenterX = rightPanelX + rightPanelW / 2;
        int previewCenterY = rightPanelY + rightPanelH / 2;

        if (minecraft != null && minecraft.player != null) {
            renderPlayerModel3D(context, previewCenterX, previewCenterY, mouseX, mouseY);
        } else {
            context.centeredText(font, Component.translatable("wskinloader.preview.error.need_world"),
                    previewCenterX, previewCenterY, TEXT_ERROR);
        }

        // ── 5. 渲染按钮 (super 会渲染 addRenderableWidget 注册的按钮) ──
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    /**
     * 渲染3D玩家模型。
     * 修复：清除盔甲装备，避免预览其他玩家时显示当前玩家的盔甲。
     */
    private void renderPlayerModel3D(GuiGraphicsExtractor graphics, int centerX, int centerY, int mouseX, int mouseY) {
        try {
            // 优先使用目标玩家实体（有正确的皮肤和披风）
            LivingEntity entity = findPlayerEntity();
            
            // 如果目标玩家不在世界中，使用当前玩家作为模型基础
            // 但会通过 skinTexture 替换皮肤
            if (entity == null) {
                entity = minecraft.player;
            }
            
            if (entity == null) {
                graphics.centeredText(font, Component.translatable("wskinloader.preview.error.need_world"), centerX, centerY, TEXT_ERROR);
                return;
            }

            // 使用面板大小作为渲染区域，留出边距
            int panelW = width / 2 - 40;
            int panelH = height - 100;
            int margin = 20;
            int x0 = centerX - panelW / 2 + margin;
            int y0 = centerY - panelH / 2 + margin + 10; // +10 for title
            int x1 = centerX + panelW / 2 - margin;
            int y1 = centerY + panelH / 2 - margin;

            // 添加 scissor 裁剪，防止模型溢出预览框
            graphics.enableScissor(x0, y0, x1, y1);

            // 确定使用的皮肤和披风纹理
            // 优先使用传入的 skinTexture，其次从 SkinCache 获取
            ClientAsset.Texture skinToUse = skinTexture;
            if (skinToUse == null && playerUuid != null) {
                skinToUse = SkinCache.getSkin(playerUuid);
            }
            ClientAsset.Texture capeToUse = null;
            if (playerUuid != null) {
                capeToUse = SkinCache.getCape(playerUuid);
            }

            // 判断是否使用了当前玩家作为模型基础（目标不在世界中）
            boolean usingCurrentPlayerAsBase = (entity == minecraft.player && playerUuid != null && !entity.getUUID().equals(playerUuid));

            // 始终使用自定义纹理渲染（确保不显示当前玩家的披风）
            extractEntityWithCustomTextures(
                    graphics, x0, y0, x1, y1, modelSize, 0.0625F,
                    rotationX, rotationY, entity, skinToUse, capeToUse,
                    usingCurrentPlayerAsBase);

            graphics.disableScissor();
        } catch (Exception e) {
            graphics.centeredText(font, Component.translatable("wskinloader.preview.error.render_failed"), centerX, centerY, TEXT_ERROR);
        }
    }

    /**
     * 根据UUID或玩家名称查找玩家实体。
     */
    private LivingEntity findPlayerEntity() {
        if (minecraft == null || minecraft.level == null) return null;

        if (playerUuid != null) {
            for (net.minecraft.client.player.AbstractClientPlayer player : minecraft.level.players()) {
                if (player.getUUID().equals(playerUuid)) {
                    return player;
                }
            }
        }

        if (playerName != null) {
            for (net.minecraft.client.player.AbstractClientPlayer player : minecraft.level.players()) {
                if (player.getName().getString().equals(playerName)) {
                    return player;
                }
            }
        }

        // 不再 fallback 到当前玩家，避免显示错误的皮肤/披风
        return null;
    }

    /**
     * 渲染实体（无自定义皮肤）。
     * 修复：清除装备避免盔甲穿透。
     */
    private static void extractEntityInInventoryFollowsMouse(
            GuiGraphicsExtractor graphics,
            int x0, int y0, int x1, int y1,
            int size, float offsetY,
            float rotX, float rotY,
            LivingEntity entity) {

        // 使用 Quaternion 控制旋转 — 只允许 Y 轴旋转（水平转盘）
        Quaternionf rotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateY(rotX * (float)(Math.PI / 180.0));
        Quaternionf xRotation = new Quaternionf(); // 无垂直倾斜

        EntityRenderState renderState = extractRenderState(entity);
        applyPoseToRenderState(renderState, rotX, rotY);

        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + offsetY, 0.0F);
        graphics.entity(renderState, size, translation, rotation, xRotation, x0, y0, x1, y1);
    }

    /**
     * 渲染实体（带自定义皮肤）。
     * 修复：清除装备避免盔甲穿透。
     */
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
                .rotateY(rotX * (float)(Math.PI / 180.0));
        Quaternionf xRotation = new Quaternionf();

        EntityRenderState renderState = extractRenderState(entity);

        // 替换皮肤和披风纹理，并自适应模型类型（宽/瘦）
        if (renderState instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatarState) {
            net.minecraft.world.entity.player.PlayerSkin currentSkin = avatarState.skin;
            
            // 确定正确的模型类型
            net.minecraft.world.entity.player.PlayerModelType modelType;
            if (modelTypeOverride) {
                modelType = forceSlim ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE;
            } else {
                modelType = currentSkin.model();
                if (playerUuid != null && minecraft != null && minecraft.getConnection() != null) {
                    net.minecraft.client.multiplayer.PlayerInfo info = minecraft.getConnection().getPlayerInfo(playerUuid);
                    if (info != null) {
                        modelType = info.getSkin().model();
                    }
                }
            }
            
            // 确定披风：如果 stripEntityCape=true 且没有自定义披风，不显示披风
            ClientAsset.Texture capeToRender = customCape;
            if (capeToRender == null && !stripEntityCape) {
                capeToRender = currentSkin.cape(); // 保留实体自身的披风
            }
            // 如果 stripEntityCape=true 且 customCape==null，capeToRender 保持 null（不显示披风）
            
            avatarState.skin = new net.minecraft.world.entity.player.PlayerSkin(
                    customSkin != null ? customSkin : currentSkin.body(),
                    capeToRender,
                    currentSkin.elytra(),
                    modelType,
                    currentSkin.secure()
            );
        }

        applyPoseToRenderState(renderState, rotX, rotY);

        Vector3f translation = new Vector3f(0.0F, renderState.boundingBoxHeight / 2.0F + offsetY, 0.0F);
        graphics.entity(renderState, size, translation, rotation, xRotation, x0, y0, x1, y1);
    }

    /**
     * 应用姿态到渲染状态。
     */
    private static void applyPoseToRenderState(EntityRenderState renderState, float bodyRotDeg, float headTiltDeg) {
        if (renderState instanceof LivingEntityRenderState livingState) {
            // 所有旋转由 Quaternion 控制，pose 中不做旋转
            livingState.bodyRot = 0;
            livingState.yRot = 0;
            livingState.xRot = 0;

            // 清除头部物品
            livingState.wornHeadType = null;
            livingState.wornHeadProfile = null;
            livingState.wornHeadAnimationPos = 0;

            // 清除状态效果
            livingState.isFullyFrozen = false;
            livingState.hasRedOverlay = false;
            livingState.isAutoSpinAttack = false;
            livingState.isInWater = false;
            livingState.deathTime = 0;
            livingState.walkAnimationPos = 0;
            livingState.walkAnimationSpeed = 0;

            // 清除盔甲
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

            // 清除手持物品
            if (livingState instanceof net.minecraft.client.renderer.entity.state.ArmedEntityRenderState armedState) {
                armedState.rightHandItemStack = ItemStack.EMPTY;
                armedState.leftHandItemStack = ItemStack.EMPTY;
                armedState.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
                armedState.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.EMPTY;
                armedState.attackTime = 0;
            }

            // 清除玩家特有的装饰（箭矢、蜜蜂刺、肩膀鹦鹉等）
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

    /**
     * 提取实体渲染状态。
     */
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
        modelSize += (int) (verticalAmount * 5);
        modelSize = Math.max(30, Math.min(90, modelSize));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();

            // Check model type button click
            int leftPanelX = 20;
            int leftPanelY = 50;
            int infoX = leftPanelX + 12;
            // Calculate infoY to match rendering position of model type button
            int infoY = leftPanelY + 12; // start
            infoY += 14; // after title
            infoY += 8; // after separator
            infoY += 14; // after name
            if (playerUuid != null) {
                infoY += 10; // uuid line 1
                infoY += 14; // uuid line 2
            }
            infoY += 18; // after skin status
            infoY += 10; // after separator
            infoY += 14; // after controls title
            infoY += 11; // after drag
            infoY += 11; // after zoom control
            infoY += 16; // after esc
            infoY += 14; // after zoom info text (zoom: xx%)

            // Model type buttons (WIDE / SLIM)
            int mLeftPanelW = width / 2 - 40;
            int mInfoX = 20 + 12;
            int btnW = (mLeftPanelW - 12 * 2 - 8) / 2;
            int btnH = 18;
            int wideX = mInfoX;
            int slimX = mInfoX + btnW + 8;
            
            if (modelBtnY > 0 && mx >= wideX && mx < wideX + btnW && my >= modelBtnY && my < modelBtnY + btnH) {
                forceSlim = false;
                modelTypeOverride = true;
                return true;
            }
            if (modelBtnY > 0 && mx >= slimX && mx < slimX + btnW && my >= modelBtnY && my < modelBtnY + btnH) {
                forceSlim = true;
                modelTypeOverride = true;
                return true;
            }

            dragging = true;
            dragStartX = event.x();
            dragStartY = event.y();
            dragStartRotX = rotationX;
            dragStartRotY = rotationY;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }

    // ─── Utility ───

    private void drawOutline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int lighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 20);
        int b = Math.min(255, (color & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
