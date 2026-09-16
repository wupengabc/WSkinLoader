package com.wupeng.wskinloader.client.config.screen;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.config.ModConfig.ApiConfig;
import com.wupeng.wskinloader.client.config.ModConfig.ConfigData;
import com.wupeng.wskinloader.client.config.ModConfig.PlayerOverride;
import com.wupeng.wskinloader.client.render.RenderBackendInfo;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.PlayerSkinSourceCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WSkinLoader 配置界面 - 简单可靠的实现，仅使用 Minecraft 内置渲染方法。
 */
public class ConfigScreen extends Screen {

    // ─── Colors (ARGB) ───
    private static final int BG_OVERLAY = 0xCC1A1A2E;
    private static final int BG_SIDEBAR = 0xFF161620;
    private static final int BG_CONTENT = 0xFF1E1E28;
    private static final int BG_TITLEBAR = 0xFF111118;
    private static final int BG_INFOPANEL = 0xFF222230;
    private static final int BG_BUTTON = 0xFF2A2A38;
    private static final int BG_BUTTON_HOVER = 0xFF3A3A48;
    private static final int BG_BUTTON_PRIMARY = 0xFF2BB673;
    private static final int BG_BUTTON_DANGER = 0xFFE04848;
    private static final int BG_INPUT = 0xFF222230;
    private static final int BG_SELECTED = 0xFF1A7A4C;
    private static final int BORDER = 0xFF2A2A38;
    private static final int BORDER_FOCUS = 0xFF2BB673;
    private static final int TEXT_PRIMARY = 0xFFE0E0F0;
    private static final int TEXT_SECONDARY = 0xFF8A8A9A;
    private static final int TEXT_MUTED = 0xFF5A5A6A;
    private static final int ACCENT = 0xFF2BB673;
    private static final int SEPARATOR = 0xFF2A2A38;
    private static final int TOGGLE_ON = 0xFF4CAF50;
    private static final int TOGGLE_OFF = 0xFF555566;

    // ─── Layout constants ───
    private static final int TITLEBAR_HEIGHT = 36;
    private static final int SIDEBAR_WIDTH = 140;
    private static final int INFOPANEL_WIDTH = 180;
    private static final int SIDEBAR_ITEM_HEIGHT = 24;
    private static final int BUTTON_HEIGHT = 20;

    // ─── Tab keys ───
    private static final String[] TAB_KEYS = {
        "wskinloader.config.tab.general",
        "wskinloader.config.tab.skin_api",
        "wskinloader.config.tab.cape_api",
        "wskinloader.config.tab.player_override",
        "wskinloader.config.tab.cache"
    };

    // ─── State ───
    private final Screen parent;
    private ConfigData editData;
    private int selectedTab = 0;
    private boolean dirty = false;
    private int scrollOffset = 0;

    // ─── Layout (computed in init) ───
    private int contentX, contentY, contentW, contentH;
    private int sidebarX, sidebarY, sidebarW, sidebarH;
    private int infoPanelX, infoPanelY, infoPanelW, infoPanelH;

    // ─── Edit fields for API add/edit ───
    private EditBox urlField;
    private EditBox aliasField;
    private EditBox playerNameField;
    private boolean addingApi = false;
    private int editingApiIndex = -1;
    private boolean addingPlayer = false;

    // ─── Mouse tracking ───
    private int mouseXPos, mouseYPos;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("wskinloader.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // Deep copy config for editing
        this.editData = ModConfig.getInstance().deepCopy();

        // Compute layout regions
        sidebarX = 0;
        sidebarY = TITLEBAR_HEIGHT;
        sidebarW = SIDEBAR_WIDTH;
        sidebarH = this.height - TITLEBAR_HEIGHT;

        infoPanelX = this.width - INFOPANEL_WIDTH;
        infoPanelY = TITLEBAR_HEIGHT;
        infoPanelW = INFOPANEL_WIDTH;
        infoPanelH = this.height - TITLEBAR_HEIGHT;

        contentX = SIDEBAR_WIDTH;
        contentY = TITLEBAR_HEIGHT;
        contentW = this.width - SIDEBAR_WIDTH - INFOPANEL_WIDTH;
        contentH = this.height - TITLEBAR_HEIGHT;

        // Reset edit state
        addingApi = false;
        editingApiIndex = -1;
        addingPlayer = false;
        scrollOffset = 0;
        recreateFields();
    }

    private void recreateFields() {
        // Remove old widgets
        if (urlField != null) removeWidget(urlField);
        if (aliasField != null) removeWidget(aliasField);
        if (playerNameField != null) removeWidget(playerNameField);
        urlField = null;
        aliasField = null;
        playerNameField = null;

        if (addingApi || editingApiIndex >= 0) {
            // Position fields below the form title and labels
            int labelOffset = 50;
            int fieldX = contentX + 20 + labelOffset;
            int fieldY = contentY + 62;
            int fieldW = contentW - 40 - labelOffset;

            aliasField = new EditBox(font, fieldX, fieldY, fieldW, 18, Component.translatable("wskinloader.config.api.alias"));
            aliasField.setMaxLength(64);
            addRenderableWidget(aliasField);

            urlField = new EditBox(font, fieldX, fieldY + 28, fieldW, 18, Component.translatable("wskinloader.config.api.url"));
            urlField.setMaxLength(512);
            addRenderableWidget(urlField);

            // Pre-fill if editing
            if (editingApiIndex >= 0) {
                List<ApiConfig> apis = getActiveApiList();
                if (editingApiIndex < apis.size()) {
                    ApiConfig api = apis.get(editingApiIndex);
                    aliasField.setValue(api.alias != null ? api.alias : "");
                    urlField.setValue(api.url != null ? api.url : "");
                }
            }
        }

        if (addingPlayer) {
            int labelOffset = 50;
            int fieldX = contentX + 20 + labelOffset;
            int fieldY = contentY + 62;
            int fieldW = contentW - 40 - labelOffset;

            playerNameField = new EditBox(font, fieldX, fieldY, fieldW, 18, Component.translatable("wskinloader.config.override.name_label"));
            playerNameField.setMaxLength(32);
            addRenderableWidget(playerNameField);
        }
    }

    private List<ApiConfig> getActiveApiList() {
        return selectedTab == 1 ? editData.skinApis : editData.capeApis;
    }

    // ═══════════════════════════════════════════════════════════════════
    // RENDERING
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        this.mouseXPos = mouseX;
        this.mouseYPos = mouseY;

        // 1. Full-screen overlay background
        ctx.fill(0, 0, this.width, this.height, BG_OVERLAY);

        // 2. Title bar
        renderTitleBar(ctx, mouseX, mouseY);

        // 3. Sidebar
        renderSidebar(ctx, mouseX, mouseY);

        // 4. Content area
        renderContent(ctx, mouseX, mouseY);

        // 5. Info panel
        renderInfoPanel(ctx, mouseX, mouseY);

        // 6. Render widgets (EditBox, etc.)
        super.extractRenderState(ctx, mouseX, mouseY, delta);
    }

    // ─── Title Bar ───
    private void renderTitleBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, this.width, TITLEBAR_HEIGHT, BG_TITLEBAR);
        // Bottom separator
        ctx.fill(0, TITLEBAR_HEIGHT - 1, this.width, TITLEBAR_HEIGHT, SEPARATOR);

        // Logo "W" chip
        int logoX = 8;
        int logoY = (TITLEBAR_HEIGHT - 16) / 2;
        ctx.fill(logoX, logoY, logoX + 16, logoY + 16, ACCENT);
        int charX = logoX + (16 - font.width("W")) / 2;
        int charY = logoY + (16 - 9) / 2;
        ctx.text(font, "W", charX, charY, 0xFFFFFFFF, false);

        // Title text
        Component titleText = Component.translatable("wskinloader.config.title");
        int titleX = logoX + 16 + 8;
        int titleY = (TITLEBAR_HEIGHT - 9) / 2;
        ctx.text(font, titleText, titleX, titleY, TEXT_PRIMARY, false);

        // Dirty indicator
        if (dirty) {
            int dotX = titleX + font.width(titleText) + 6;
            int dotY = (TITLEBAR_HEIGHT - 4) / 2;
            ctx.fill(dotX, dotY, dotX + 4, dotY + 4, ACCENT);
        }

        // Cancel button (right side)
        Component cancelText = Component.translatable("wskinloader.config.button.cancel_icon");
        Component saveText = Component.translatable("wskinloader.config.button.save_icon");
        int cancelW = font.width(cancelText) + 16;
        int saveW = font.width(saveText) + 16;
        int btnY = (TITLEBAR_HEIGHT - BUTTON_HEIGHT) / 2;
        int saveX = this.width - 12 - saveW;
        int cancelX = saveX - 8 - cancelW;

        drawButton(ctx, cancelX, btnY, cancelW, BUTTON_HEIGHT, cancelText, BG_BUTTON, TEXT_PRIMARY,
                isInside(mouseX, mouseY, cancelX, btnY, cancelW, BUTTON_HEIGHT));

        // Save button
        int saveBg = dirty ? BG_BUTTON_PRIMARY : BG_BUTTON;
        drawButton(ctx, saveX, btnY, saveW, BUTTON_HEIGHT, saveText, saveBg, 0xFFFFFFFF,
                isInside(mouseX, mouseY, saveX, btnY, saveW, BUTTON_HEIGHT));
    }

    // ─── Sidebar ───
    private void renderSidebar(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(sidebarX, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, BG_SIDEBAR);
        // Right border
        ctx.fill(sidebarX + sidebarW - 1, sidebarY, sidebarX + sidebarW, sidebarY + sidebarH, SEPARATOR);

        // Header
        int headerY = sidebarY + 8;
        ctx.text(font, Component.translatable("wskinloader.config.sidebar.header"), sidebarX + 12, headerY, TEXT_MUTED, false);

        // Tab items
        int itemY = headerY + 16;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int iy = itemY + i * SIDEBAR_ITEM_HEIGHT;
            boolean selected = (i == selectedTab);
            boolean hovered = isInside(mouseX, mouseY, sidebarX, iy, sidebarW - 1, SIDEBAR_ITEM_HEIGHT);

            if (selected) {
                ctx.fill(sidebarX + 4, iy, sidebarX + sidebarW - 5, iy + SIDEBAR_ITEM_HEIGHT, BG_SELECTED);
            } else if (hovered) {
                ctx.fill(sidebarX + 4, iy, sidebarX + sidebarW - 5, iy + SIDEBAR_ITEM_HEIGHT, BG_BUTTON_HOVER);
            }

            int textY = iy + (SIDEBAR_ITEM_HEIGHT - 9) / 2;
            int textColor = selected ? 0xFFFFFFFF : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY);
            ctx.text(font, Component.translatable(TAB_KEYS[i]), sidebarX + 16, textY, textColor, false);
        }
    }

    // ─── Content Area ───
    private void renderContent(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(contentX, contentY, contentX + contentW, contentY + contentH, BG_CONTENT);

        // Tab title
        int titleY = contentY + 12;
        ctx.text(font, Component.translatable(TAB_KEYS[selectedTab]), contentX + 16, titleY, TEXT_PRIMARY, false);

        switch (selectedTab) {
            case 0 -> renderGeneralTab(ctx, mouseX, mouseY);
            case 1 -> renderApiTab(ctx, mouseX, mouseY, editData.skinApis, "wskinloader.config.tab.skin_api");
            case 2 -> renderApiTab(ctx, mouseX, mouseY, editData.capeApis, "wskinloader.config.tab.cape_api");
            case 3 -> renderPlayerOverrideTab(ctx, mouseX, mouseY);
            case 4 -> renderCacheTab(ctx, mouseX, mouseY);
        }
    }

    // ─── Tab 0: General Settings ───
    private void renderGeneralTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int startY = contentY + 36;
        int leftX = contentX + 16;

        // Toggle 1: enableTabListHeads
        renderToggle(ctx, leftX, startY, Component.translatable("wskinloader.config.general.enable_tab_heads"), editData.enableTabListHeads, mouseX, mouseY, 0);

        // Toggle 2: enableNameTagLabel
        renderToggle(ctx, leftX, startY + 36, Component.translatable("wskinloader.config.general.enable_nametag_label"), editData.enableNameTagLabel, mouseX, mouseY, 1);

        // Active render API follows Minecraft's current graphics backend.
        ctx.text(font,
                Component.translatable("wskinloader.config.general.render_api", RenderBackendInfo.currentApiName()),
                leftX,
                startY + 80,
                TEXT_PRIMARY,
                false);
        ctx.text(font,
                Component.translatable("wskinloader.config.general.render_api.desc"),
                leftX,
                startY + 92,
                TEXT_MUTED,
                false);
    }

    private void renderToggle(GuiGraphicsExtractor ctx, int x, int y, Component label, boolean value, int mouseX, int mouseY, int toggleId) {
        // Label
        ctx.text(font, label, x, y + 4, TEXT_PRIMARY, false);

        // Toggle track
        int trackX = x + font.width(label) + 12;
        int trackY = y + 2;
        int trackW = 32;
        int trackH = 14;
        int trackColor = value ? TOGGLE_ON : TOGGLE_OFF;
        ctx.fill(trackX, trackY, trackX + trackW, trackY + trackH, trackColor);
        drawOutline(ctx, trackX, trackY, trackW, trackH, BORDER);

        // Toggle knob
        int knobSize = 10;
        int knobX = value ? (trackX + trackW - knobSize - 2) : (trackX + 2);
        int knobY = trackY + 2;
        ctx.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, 0xFFFFFFFF);

        // Status text
        Component statusText = value ? Component.translatable("wskinloader.config.toggle.on") : Component.translatable("wskinloader.config.toggle.off");
        ctx.text(font, statusText, trackX + trackW + 6, y + 4, TEXT_SECONDARY, false);
    }

    // ─── Tab 1/2: API Management ───
    private void renderApiTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY, List<ApiConfig> apis, String typeKey) {
        int startY = contentY + 30;
        int leftX = contentX + 16;

        // Hint text
        ctx.text(font, Component.translatable("wskinloader.config.hint.url"), leftX, startY, TEXT_MUTED, false);
        startY += 16;

        // Add button
        Component addBtnText = Component.translatable("wskinloader.config.button.add_api");
        if (!addingApi && editingApiIndex < 0) {
            int addBtnW = font.width(addBtnText) + 16;
            drawButton(ctx, leftX, startY, addBtnW, BUTTON_HEIGHT, addBtnText, BG_BUTTON, TEXT_PRIMARY,
                    isInside(mouseX, mouseY, leftX, startY, addBtnW, BUTTON_HEIGHT));
            startY += BUTTON_HEIGHT + 12;
        }

        // Add/Edit form
        if (addingApi || editingApiIndex >= 0) {
            int formY = startY;
            Component formTitle = addingApi
                    ? Component.translatable("wskinloader.config.api.add_title", Component.translatable(typeKey).getString())
                    : Component.translatable("wskinloader.config.api.edit_title", Component.translatable(typeKey).getString());
            ctx.text(font, formTitle, leftX, formY, ACCENT, false);
            formY += 16;

            // "别名:" label on the left, EditBox on the right
            ctx.text(font, Component.translatable("wskinloader.config.api.alias_label"), leftX, formY + 5, TEXT_SECONDARY, false);
            formY += 28;
            // "URL:" label on the left, EditBox on the right
            ctx.text(font, Component.translatable("wskinloader.config.api.url_label"), leftX, formY + 5, TEXT_SECONDARY, false);
            formY += 28;

            // Confirm / Cancel buttons
            formY += 12;
            Component confirmText = Component.translatable("wskinloader.config.button.confirm");
            Component cancelFormText = Component.translatable("wskinloader.config.button.cancel");
            int confirmW = font.width(confirmText) + 16;
            int cancelFormW = font.width(cancelFormText) + 16;
            drawButton(ctx, leftX, formY, confirmW, BUTTON_HEIGHT, confirmText, BG_BUTTON_PRIMARY, 0xFFFFFFFF,
                    isInside(mouseX, mouseY, leftX, formY, confirmW, BUTTON_HEIGHT));
            drawButton(ctx, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT, cancelFormText, BG_BUTTON, TEXT_PRIMARY,
                    isInside(mouseX, mouseY, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT));
            startY = formY + BUTTON_HEIGHT + 16;
        } else {
            // API list with scissor for scrolling
            int listY = startY;
            int listH = contentY + contentH - listY - 8;
            ctx.enableScissor(contentX, listY, contentX + contentW, listY + listH);

            int itemY = listY - scrollOffset;
            for (int i = 0; i < apis.size(); i++) {
                ApiConfig api = apis.get(i);
                int rowH = 40;
                if (itemY + rowH > listY - rowH && itemY < listY + listH + rowH) {
                    // Row background
                    boolean rowHovered = isInside(mouseX, mouseY, leftX, itemY, contentW - 32, rowH)
                            && mouseY >= listY && mouseY < listY + listH;
                    if (rowHovered) {
                        ctx.fill(leftX, itemY, leftX + contentW - 32, itemY + rowH, BG_BUTTON_HOVER);
                    }
                    ctx.fill(leftX, itemY + rowH - 1, leftX + contentW - 32, itemY + rowH, SEPARATOR);

                    // Alias + URL
                    String alias = api.alias != null && !api.alias.isEmpty() ? api.alias : Component.translatable("wskinloader.config.api.no_alias").getString();
                    ctx.text(font, alias, leftX + 4, itemY + 4, TEXT_PRIMARY, false);
                    String urlDisplay = api.url != null ? api.url : "";
                    if (font.width(urlDisplay) > contentW - 120) {
                        urlDisplay = urlDisplay.substring(0, Math.min(urlDisplay.length(), 40)) + "...";
                    }
                    ctx.text(font, urlDisplay, leftX + 4, itemY + 16, TEXT_MUTED, false);

                    // Edit button
                    Component editText = Component.translatable("wskinloader.config.button.edit");
                    int editBtnX = leftX + contentW - 32 - 80;
                    int editBtnY = itemY + 10;
                    drawButton(ctx, editBtnX, editBtnY, 34, 16, editText, BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, editBtnX, editBtnY, 34, 16));

                    // Delete button
                    Component deleteText = Component.translatable("wskinloader.config.button.delete");
                    int delBtnX = editBtnX + 38;
                    drawButton(ctx, delBtnX, editBtnY, 34, 16, deleteText, BG_BUTTON_DANGER, 0xFFFFFFFF,
                            isInside(mouseX, mouseY, delBtnX, editBtnY, 34, 16));
                }
                itemY += rowH;
            }

            if (apis.isEmpty()) {
                ctx.text(font, Component.translatable("wskinloader.config.api.empty"), leftX + 4, listY + 4, TEXT_MUTED, false);
            }

            ctx.disableScissor();
        }
    }

    // ─── Tab 3: Player Overrides ───
    private String editingPlayerName = null;

    private void renderPlayerOverrideTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int startY = contentY + 30;
        int leftX = contentX + 16;

        ctx.text(font, Component.translatable("wskinloader.config.hint.player_override"), leftX, startY, TEXT_MUTED, false);
        startY += 16;

        // Add button
        Component addPlayerText = Component.translatable("wskinloader.config.button.add_player");
        if (!addingPlayer && editingPlayerName == null) {
            int addBtnW = font.width(addPlayerText) + 16;
            drawButton(ctx, leftX, startY, addBtnW, BUTTON_HEIGHT, addPlayerText, BG_BUTTON, TEXT_PRIMARY,
                    isInside(mouseX, mouseY, leftX, startY, addBtnW, BUTTON_HEIGHT));
            startY += BUTTON_HEIGHT + 12;
        }

        // Add form
        if (addingPlayer) {
            int formY = startY;
            ctx.text(font, Component.translatable("wskinloader.config.override.add_title"), leftX, formY, ACCENT, false);
            formY += 16;
            ctx.text(font, Component.translatable("wskinloader.config.override.name_label"), leftX, formY + 5, TEXT_SECONDARY, false);
            formY += 28;

            formY += 8;
            Component confirmText = Component.translatable("wskinloader.config.button.confirm");
            Component cancelFormText = Component.translatable("wskinloader.config.button.cancel");
            int confirmW = font.width(confirmText) + 16;
            int cancelFormW = font.width(cancelFormText) + 16;
            drawButton(ctx, leftX, formY, confirmW, BUTTON_HEIGHT, confirmText, BG_BUTTON_PRIMARY, 0xFFFFFFFF,
                    isInside(mouseX, mouseY, leftX, formY, confirmW, BUTTON_HEIGHT));
            drawButton(ctx, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT, cancelFormText, BG_BUTTON, TEXT_PRIMARY,
                    isInside(mouseX, mouseY, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT));
            startY = formY + BUTTON_HEIGHT + 16;
        } else if (editingPlayerName != null) {
            // ── 编辑玩家覆盖规则 ──
            PlayerOverride override = editData.playerOverrides.get(editingPlayerName);
            if (override != null) {
                int formY = startY;
                ctx.text(font, Component.translatable("wskinloader.config.override.edit_title", editingPlayerName), leftX, formY, ACCENT, false);
                formY += 18;

                // 跳过正版检测 toggle
                Component skipLabel = Component.translatable("wskinloader.config.override.skip_premium");
                ctx.text(font, skipLabel, leftX, formY + 3, TEXT_SECONDARY, false);
                int toggleX = leftX + font.width(skipLabel) + 8;
                Component skipValue = override.skipPremiumCheck
                        ? Component.translatable("wskinloader.config.toggle.on_bracket")
                        : Component.translatable("wskinloader.config.toggle.off_bracket");
                int skipColor = override.skipPremiumCheck ? TOGGLE_ON : TEXT_MUTED;
                drawButton(ctx, toggleX, formY, font.width(skipValue) + 12, 16, skipValue, BG_BUTTON, skipColor,
                        isInside(mouseX, mouseY, toggleX, formY, font.width(skipValue) + 12, 16));
                formY += 22;

                // 模型类型
                Component modelLabel = Component.translatable("wskinloader.config.override.model_type");
                ctx.text(font, modelLabel, leftX, formY + 3, TEXT_SECONDARY, false);
                int modelToggleX = leftX + font.width(modelLabel) + 8;
                String modelDisplay = override.modelType;
                Component modelValue = Component.literal("[" + modelDisplay.toUpperCase() + "]");
                drawButton(ctx, modelToggleX, formY, font.width(modelValue) + 12, 16, modelValue, BG_BUTTON, ACCENT,
                        isInside(mouseX, mouseY, modelToggleX, formY, font.width(modelValue) + 12, 16));
                formY += 22;

                // 皮肤规则
                ctx.text(font, Component.translatable("wskinloader.config.override.skin"), leftX, formY + 3, TEXT_PRIMARY, false);
                formY += 16;
                Component skinApiLabel = Component.translatable("wskinloader.config.override.use_custom_api");
                ctx.text(font, skinApiLabel, leftX, formY + 3, TEXT_SECONDARY, false);
                int skinToggleX = leftX + font.width(skinApiLabel) + 8;
                Component skinValue = override.skin.useCustomApi
                        ? Component.translatable("wskinloader.config.toggle.on_bracket")
                        : Component.translatable("wskinloader.config.toggle.off_bracket");
                int skinColor = override.skin.useCustomApi ? TOGGLE_ON : TEXT_MUTED;
                drawButton(ctx, skinToggleX, formY, font.width(skinValue) + 12, 16, skinValue, BG_BUTTON, skinColor,
                        isInside(mouseX, mouseY, skinToggleX, formY, font.width(skinValue) + 12, 16));
                formY += 20;

                if (override.skin.useCustomApi) {
                    String indexDisplay = override.skin.apiIndex == -1
                            ? Component.translatable("wskinloader.config.override.all").getString()
                            : String.valueOf(override.skin.apiIndex);
                    Component skinIdxLabel = Component.translatable("wskinloader.config.override.api_index", indexDisplay);
                    ctx.text(font, skinIdxLabel, leftX, formY + 3, TEXT_MUTED, false);
                    int prevBtnX = leftX + font.width(skinIdxLabel) + 8;
                    drawButton(ctx, prevBtnX, formY, 16, 16, Component.literal("-"), BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, prevBtnX, formY, 16, 16));
                    drawButton(ctx, prevBtnX + 20, formY, 16, 16, Component.literal("+"), BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, prevBtnX + 20, formY, 16, 16));
                    formY += 20;
                }

                // 披风规则
                ctx.text(font, Component.translatable("wskinloader.config.override.cape"), leftX, formY + 3, TEXT_PRIMARY, false);
                formY += 16;
                Component capeApiLabel = Component.translatable("wskinloader.config.override.use_custom_api");
                ctx.text(font, capeApiLabel, leftX, formY + 3, TEXT_SECONDARY, false);
                int capeToggleX = leftX + font.width(capeApiLabel) + 8;
                Component capeValue = override.cape.useCustomApi
                        ? Component.translatable("wskinloader.config.toggle.on_bracket")
                        : Component.translatable("wskinloader.config.toggle.off_bracket");
                int capeColor = override.cape.useCustomApi ? TOGGLE_ON : TEXT_MUTED;
                drawButton(ctx, capeToggleX, formY, font.width(capeValue) + 12, 16, capeValue, BG_BUTTON, capeColor,
                        isInside(mouseX, mouseY, capeToggleX, formY, font.width(capeValue) + 12, 16));
                formY += 20;

                if (override.cape.useCustomApi) {
                    String capeIndexDisplay = override.cape.apiIndex == -1
                            ? Component.translatable("wskinloader.config.override.all").getString()
                            : String.valueOf(override.cape.apiIndex);
                    Component capeIdxLabel = Component.translatable("wskinloader.config.override.api_index", capeIndexDisplay);
                    ctx.text(font, capeIdxLabel, leftX, formY + 3, TEXT_MUTED, false);
                    int cprevBtnX = leftX + font.width(capeIdxLabel) + 8;
                    drawButton(ctx, cprevBtnX, formY, 16, 16, Component.literal("-"), BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, cprevBtnX, formY, 16, 16));
                    drawButton(ctx, cprevBtnX + 20, formY, 16, 16, Component.literal("+"), BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, cprevBtnX + 20, formY, 16, 16));
                    formY += 20;
                }

                formY += 8;
                Component doneText = Component.translatable("wskinloader.config.button.done");
                int doneW = font.width(doneText) + 16;
                drawButton(ctx, leftX, formY, doneW, BUTTON_HEIGHT, doneText, BG_BUTTON_PRIMARY, 0xFFFFFFFF,
                        isInside(mouseX, mouseY, leftX, formY, doneW, BUTTON_HEIGHT));
            }
        } else {
            // Player list
            int listY = startY;
            int listH = contentY + contentH - listY - 8;
            ctx.enableScissor(contentX, listY, contentX + contentW, listY + listH);

            int itemY = listY - scrollOffset;
            List<String> playerNames = new ArrayList<>(editData.playerOverrides.keySet());
            for (int i = 0; i < playerNames.size(); i++) {
                String name = playerNames.get(i);
                PlayerOverride override = editData.playerOverrides.get(name);
                int rowH = 32;

                if (itemY + rowH > listY - rowH && itemY < listY + listH + rowH) {
                    boolean rowHovered = isInside(mouseX, mouseY, leftX, itemY, contentW - 32, rowH)
                            && mouseY >= listY && mouseY < listY + listH;
                    if (rowHovered) {
                        ctx.fill(leftX, itemY, leftX + contentW - 32, itemY + rowH, BG_BUTTON_HOVER);
                    }
                    ctx.fill(leftX, itemY + rowH - 1, leftX + contentW - 32, itemY + rowH, SEPARATOR);

                    // Player name
                    ctx.text(font, name, leftX + 4, itemY + 4, TEXT_PRIMARY, false);

                    // Info summary
                    String skipStr = override.skipPremiumCheck
                            ? Component.translatable("wskinloader.config.override.yes").getString()
                            : Component.translatable("wskinloader.config.override.no").getString();
                    String skinStr = override.skin.useCustomApi
                            ? Component.translatable("wskinloader.config.override.custom").getString()
                            : Component.translatable("wskinloader.config.override.premium").getString();
                    String capeStr = override.cape.useCustomApi
                            ? Component.translatable("wskinloader.config.override.custom").getString()
                            : Component.translatable("wskinloader.config.override.premium").getString();
                    Component info = Component.translatable("wskinloader.config.override.info_summary", skipStr, skinStr, capeStr);
                    ctx.text(font, info, leftX + 4, itemY + 16, TEXT_MUTED, false);

                    // Edit button
                    Component editText = Component.translatable("wskinloader.config.button.edit");
                    int editBtnX = leftX + contentW - 32 - 80;
                    int editBtnY = itemY + 8;
                    drawButton(ctx, editBtnX, editBtnY, 34, 16, editText, BG_BUTTON, TEXT_PRIMARY,
                            isInside(mouseX, mouseY, editBtnX, editBtnY, 34, 16));

                    // Delete button
                    Component deleteText = Component.translatable("wskinloader.config.button.delete");
                    int delBtnX = editBtnX + 38;
                    drawButton(ctx, delBtnX, editBtnY, 34, 16, deleteText, BG_BUTTON_DANGER, 0xFFFFFFFF,
                            isInside(mouseX, mouseY, delBtnX, editBtnY, 34, 16));
                }
                itemY += rowH;
            }

            if (playerNames.isEmpty()) {
                ctx.text(font, Component.translatable("wskinloader.config.override.empty"), leftX + 4, listY + 4, TEXT_MUTED, false);
            }

            ctx.disableScissor();
        }
    }

    // ─── Tab 4: Cache Management ───
    private void renderCacheTab(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        int startY = contentY + 36;
        int leftX = contentX + 16;

        // Cache stats
        List<UUID> cachedPlayers = new ArrayList<>(SkinCache.getCachedPlayers());
        int skinCount = cachedPlayers.size();
        ctx.text(font, Component.translatable("wskinloader.config.cache.stats"), leftX, startY, TEXT_PRIMARY, false);
        startY += 16;
        ctx.text(font, Component.translatable("wskinloader.config.cache.count", skinCount), leftX, startY, TEXT_SECONDARY, false);
        startY += 24;

        // Clear cache button
        Component clearText = Component.translatable("wskinloader.config.cache.clear_all");
        int clearW = font.width(clearText) + 16;
        drawButton(ctx, leftX, startY, clearW, BUTTON_HEIGHT, clearText, BG_BUTTON_DANGER, 0xFFFFFFFF,
                isInside(mouseX, mouseY, leftX, startY, clearW, BUTTON_HEIGHT));
        startY += BUTTON_HEIGHT + 12;

        // Separator
        ctx.fill(leftX, startY, leftX + contentW - 32, startY + 1, SEPARATOR);
        startY += 12;

        // Cached player list header
        ctx.text(font, Component.translatable("wskinloader.config.cache.player_list", skinCount), leftX, startY, TEXT_PRIMARY, false);
        startY += 16;

        if (cachedPlayers.isEmpty()) {
            ctx.text(font, Component.translatable("wskinloader.config.cache.empty"), leftX + 4, startY, TEXT_MUTED, false);
        } else {
            // Scrollable player list
            int listY = startY;
            int listH = contentY + contentH - listY - 8;
            ctx.enableScissor(contentX, listY, contentX + contentW, listY + listH);

            int itemY = listY - scrollOffset;
            for (int i = 0; i < cachedPlayers.size(); i++) {
                UUID uuid = cachedPlayers.get(i);
                String name = PlayerNameCache.getName(uuid);
                if (name == null) name = uuid.toString().substring(0, 8) + "...";
                String source = PlayerSkinSourceCache.getSource(uuid);
                if (source == null) source = "unknown";

                int rowH = 28;
                if (itemY + rowH > listY - rowH && itemY < listY + listH + rowH) {
                    boolean rowHovered = isInside(mouseX, mouseY, leftX, itemY, contentW - 32, rowH)
                            && mouseY >= listY && mouseY < listY + listH;
                    if (rowHovered) {
                        ctx.fill(leftX, itemY, leftX + contentW - 32, itemY + rowH, BG_BUTTON_HOVER);
                    }
                    ctx.fill(leftX, itemY + rowH - 1, leftX + contentW - 32, itemY + rowH, SEPARATOR);

                    // Player name + source
                    ctx.text(font, name, leftX + 4, itemY + 4, TEXT_PRIMARY, false);
                    ctx.text(font, "[" + source + "]", leftX + 4 + font.width(name) + 8, itemY + 4, ACCENT, false);

                    // UUID (truncated)
                    ctx.text(font, uuid.toString().substring(0, 13) + "...", leftX + 4, itemY + 15, TEXT_MUTED, false);

                    // Preview button
                    Component previewText = Component.translatable("wskinloader.config.button.preview");
                    int previewBtnX = leftX + contentW - 32 - 120;
                    int btnY = itemY + 6;
                    drawButton(ctx, previewBtnX, btnY, 40, 16, previewText, BG_BUTTON, ACCENT,
                            isInside(mouseX, mouseY, previewBtnX, btnY, 40, 16));

                    // Delete button
                    Component deleteText = Component.translatable("wskinloader.config.button.delete");
                    int delBtnX = previewBtnX + 44;
                    drawButton(ctx, delBtnX, btnY, 34, 16, deleteText, BG_BUTTON_DANGER, 0xFFFFFFFF,
                            isInside(mouseX, mouseY, delBtnX, btnY, 34, 16));
                }
                itemY += rowH;
            }

            ctx.disableScissor();
        }
    }

    // ─── Info Panel ───
    private void renderInfoPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
        ctx.fill(infoPanelX, infoPanelY, infoPanelX + infoPanelW, infoPanelY + infoPanelH, BG_INFOPANEL);
        // Left border
        ctx.fill(infoPanelX, infoPanelY, infoPanelX + 1, infoPanelY + infoPanelH, SEPARATOR);

        int px = infoPanelX + 12;
        int py = infoPanelY + 12;

        // Config stats
        ctx.text(font, Component.translatable("wskinloader.config.info.current"), px, py, TEXT_PRIMARY, false);
        py += 16;
        ctx.text(font, Component.translatable("wskinloader.config.info.skin_api_count", editData.skinApis.size()), px, py, TEXT_SECONDARY, false);
        py += 12;
        ctx.text(font, Component.translatable("wskinloader.config.info.cape_api_count", editData.capeApis.size()), px, py, TEXT_SECONDARY, false);
        py += 12;
        ctx.text(font, Component.translatable("wskinloader.config.info.override_count", editData.playerOverrides.size()), px, py, TEXT_SECONDARY, false);
        py += 12;
        ctx.text(font, Component.translatable("wskinloader.config.info.render_api", RenderBackendInfo.currentApiName()), px, py, TEXT_SECONDARY, false);
        py += 24;

        // Separator
        ctx.fill(px, py, infoPanelX + infoPanelW - 12, py + 1, SEPARATOR);
        py += 12;

        // Links
        ctx.text(font, Component.translatable("wskinloader.config.info.feedback"), px, py, ACCENT, false);
    }

    private int getFeedbackLinkY() {
        int py = infoPanelY + 12;
        py += 16; // title
        py += 12; // skin API count
        py += 12; // cape API count
        py += 12; // player override count
        py += 24; // render API row and spacing before separator
        py += 12; // spacing after separator
        return py;
    }

    // ═══════════════════════════════════════════════════════════════════
    // INPUT HANDLING
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button != 0) return super.mouseClicked(event, doubleClick);

        int mx = (int) event.x();
        int my = (int) event.y();

        // Title bar buttons
        if (my < TITLEBAR_HEIGHT) {
            Component cancelText = Component.translatable("wskinloader.config.button.cancel_icon");
            Component saveText = Component.translatable("wskinloader.config.button.save_icon");
            int cancelW = font.width(cancelText) + 16;
            int saveW = font.width(saveText) + 16;
            int btnY = (TITLEBAR_HEIGHT - BUTTON_HEIGHT) / 2;
            int saveX = this.width - 12 - saveW;
            int cancelX = saveX - 8 - cancelW;

            if (isInside(mx, my, cancelX, btnY, cancelW, BUTTON_HEIGHT)) {
                onCancel();
                return true;
            }
            if (isInside(mx, my, saveX, btnY, saveW, BUTTON_HEIGHT)) {
                onSave();
                return true;
            }
            return true;
        }

        // Sidebar clicks
        if (mx < sidebarW && my >= sidebarY) {
            int headerY = sidebarY + 8 + 16;
            for (int i = 0; i < TAB_KEYS.length; i++) {
                int iy = headerY + i * SIDEBAR_ITEM_HEIGHT;
                if (isInside(mx, my, sidebarX, iy, sidebarW - 1, SIDEBAR_ITEM_HEIGHT)) {
                    if (selectedTab != i) {
                        selectedTab = i;
                        addingApi = false;
                        editingApiIndex = -1;
                        addingPlayer = false;
                        editingPlayerName = null;
                        scrollOffset = 0;
                        recreateFields();
                    }
                    return true;
                }
            }
            return true;
        }

        // Info panel clicks (feedback link)
        if (mx >= infoPanelX && my >= infoPanelY) {
            int px = infoPanelX + 12;
            int linkY = getFeedbackLinkY();
            Component feedbackText = Component.translatable("wskinloader.config.info.feedback");
            int linkW = font.width(feedbackText);
            if (isInside(mx, my, px, linkY, linkW, 12)) {
                net.minecraft.util.Util.getPlatform().openUri("https://github.com/wupengabc/wskinloader/issues");
                return true;
            }
        }

        // Content area clicks — first let super handle EditBox focus
        if (mx >= contentX && mx < contentX + contentW && my >= contentY) {
            // Try to handle our custom buttons first
            if (handleContentClick(mx, my)) {
                return true;
            }
            // If no custom button was clicked, let super handle (EditBox focus)
            return super.mouseClicked(event, doubleClick);
        }

        return super.mouseClicked(event, doubleClick);
    }

    private boolean handleContentClick(int mx, int my) {
        int leftX = contentX + 16;

        switch (selectedTab) {
            case 0 -> {
                // Toggle clicks
                int startY = contentY + 36;
                Component label1 = Component.translatable("wskinloader.config.general.enable_tab_heads");
                if (isToggleClicked(mx, my, leftX, startY, label1, editData.enableTabListHeads)) {
                    editData.enableTabListHeads = !editData.enableTabListHeads;
                    markDirty();
                    return true;
                }
                Component label2 = Component.translatable("wskinloader.config.general.enable_nametag_label");
                if (isToggleClicked(mx, my, leftX, startY + 36, label2, editData.enableNameTagLabel)) {
                    editData.enableNameTagLabel = !editData.enableNameTagLabel;
                    markDirty();
                    return true;
                }
            }
            case 1, 2 -> {
                return handleApiTabClick(mx, my, getActiveApiList());
            }
            case 3 -> {
                return handlePlayerTabClick(mx, my);
            }
            case 4 -> {
                return handleCacheTabClick(mx, my);
            }
        }
        return false;
    }

    private boolean isToggleClicked(int mx, int my, int x, int y, Component label, boolean value) {
        int trackX = x + font.width(label) + 12;
        int trackY = y + 2;
        int trackW = 32;
        int trackH = 14;
        return isInside(mx, my, trackX, trackY, trackW, trackH);
    }

    private boolean handleApiTabClick(int mx, int my, List<ApiConfig> apis) {
        int startY = contentY + 30 + 16;
        int leftX = contentX + 16;

        if (!addingApi && editingApiIndex < 0) {
            // Add button
            Component addBtnText = Component.translatable("wskinloader.config.button.add_api");
            int addBtnW = font.width(addBtnText) + 16;
            if (isInside(mx, my, leftX, startY, addBtnW, BUTTON_HEIGHT)) {
                addingApi = true;
                recreateFields();
                return true;
            }
            startY += BUTTON_HEIGHT + 12;

            // List item clicks
            int itemY = startY - scrollOffset;
            for (int i = 0; i < apis.size(); i++) {
                int rowH = 40;
                // Edit button
                int editBtnX = leftX + contentW - 32 - 80;
                int editBtnY = itemY + 10;
                if (isInside(mx, my, editBtnX, editBtnY, 34, 16)) {
                    editingApiIndex = i;
                    recreateFields();
                    return true;
                }
                // Delete button
                int delBtnX = editBtnX + 38;
                if (isInside(mx, my, delBtnX, editBtnY, 34, 16)) {
                    apis.remove(i);
                    markDirty();
                    return true;
                }
                itemY += rowH;
            }
        } else {
            // Form confirm/cancel
            Component confirmText = Component.translatable("wskinloader.config.button.confirm");
            Component cancelFormText = Component.translatable("wskinloader.config.button.cancel");
            int formY = contentY + 30 + 16 + 16 + 28 + 28 + 12;
            int confirmW = font.width(confirmText) + 16;
            int cancelFormW = font.width(cancelFormText) + 16;

            if (isInside(mx, my, leftX, formY, confirmW, BUTTON_HEIGHT)) {
                // Confirm add/edit
                String url = urlField != null ? urlField.getValue().trim() : "";
                String alias = aliasField != null ? aliasField.getValue().trim() : "";
                if (!url.isEmpty()) {
                    if (alias.isEmpty()) alias = Component.translatable("wskinloader.config.api.no_alias").getString();
                    if (addingApi) {
                        apis.add(new ApiConfig(url, alias));
                    } else if (editingApiIndex >= 0 && editingApiIndex < apis.size()) {
                        apis.get(editingApiIndex).url = url;
                        apis.get(editingApiIndex).alias = alias;
                    }
                    markDirty();
                }
                addingApi = false;
                editingApiIndex = -1;
                recreateFields();
                return true;
            }
            if (isInside(mx, my, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT)) {
                addingApi = false;
                editingApiIndex = -1;
                recreateFields();
                return true;
            }
        }
        return false;
    }

    private boolean handlePlayerTabClick(int mx, int my) {
        int startY = contentY + 30 + 16;
        int leftX = contentX + 16;

        if (editingPlayerName != null) {
            // ── 编辑模式点击处理 ──
            PlayerOverride override = editData.playerOverrides.get(editingPlayerName);
            if (override == null) { editingPlayerName = null; return true; }

            int formY = startY + 18;

            // 跳过正版检测 toggle
            Component skipLabel = Component.translatable("wskinloader.config.override.skip_premium");
            int toggleX = leftX + font.width(skipLabel) + 8;
            Component skipValue = override.skipPremiumCheck
                    ? Component.translatable("wskinloader.config.toggle.on_bracket")
                    : Component.translatable("wskinloader.config.toggle.off_bracket");
            if (isInside(mx, my, toggleX, formY, font.width(skipValue) + 12, 16)) {
                override.skipPremiumCheck = !override.skipPremiumCheck;
                markDirty();
                return true;
            }
            formY += 22;

            // 模型类型 toggle
            Component modelLabel = Component.translatable("wskinloader.config.override.model_type");
            int modelToggleX = leftX + font.width(modelLabel) + 8;
            String modelDisplay = override.modelType;
            Component modelValue = Component.literal("[" + modelDisplay.toUpperCase() + "]");
            if (isInside(mx, my, modelToggleX, formY, font.width(modelValue) + 12, 16)) {
                // Cycle: auto → slim → wide → auto
                override.modelType = switch (override.modelType) {
                    case "auto" -> "slim";
                    case "slim" -> "wide";
                    default -> "auto";
                };
                markDirty();
                return true;
            }
            formY += 22;

            // 皮肤 useCustomApi toggle
            formY += 16; // "皮肤:" label
            Component skinApiLabel = Component.translatable("wskinloader.config.override.use_custom_api");
            int skinToggleX = leftX + font.width(skinApiLabel) + 8;
            Component skinValue = override.skin.useCustomApi
                    ? Component.translatable("wskinloader.config.toggle.on_bracket")
                    : Component.translatable("wskinloader.config.toggle.off_bracket");
            if (isInside(mx, my, skinToggleX, formY, font.width(skinValue) + 12, 16)) {
                override.skin.useCustomApi = !override.skin.useCustomApi;
                markDirty();
                return true;
            }
            formY += 20;

            // 皮肤 apiIndex +/- buttons
            if (override.skin.useCustomApi) {
                String indexDisplay = override.skin.apiIndex == -1
                        ? Component.translatable("wskinloader.config.override.all").getString()
                        : String.valueOf(override.skin.apiIndex);
                Component skinIdxLabel = Component.translatable("wskinloader.config.override.api_index", indexDisplay);
                int prevBtnX = leftX + font.width(skinIdxLabel) + 8;
                if (isInside(mx, my, prevBtnX, formY, 16, 16)) {
                    override.skin.apiIndex = Math.max(-1, override.skin.apiIndex - 1);
                    markDirty();
                    return true;
                }
                if (isInside(mx, my, prevBtnX + 20, formY, 16, 16)) {
                    override.skin.apiIndex = Math.min(editData.skinApis.size() - 1, override.skin.apiIndex + 1);
                    markDirty();
                    return true;
                }
                formY += 20;
            }

            // 披风 useCustomApi toggle
            formY += 16; // "披风:" label
            Component capeApiLabel = Component.translatable("wskinloader.config.override.use_custom_api");
            int capeToggleX = leftX + font.width(capeApiLabel) + 8;
            Component capeValue = override.cape.useCustomApi
                    ? Component.translatable("wskinloader.config.toggle.on_bracket")
                    : Component.translatable("wskinloader.config.toggle.off_bracket");
            if (isInside(mx, my, capeToggleX, formY, font.width(capeValue) + 12, 16)) {
                override.cape.useCustomApi = !override.cape.useCustomApi;
                markDirty();
                return true;
            }
            formY += 20;

            // 披风 apiIndex +/- buttons
            if (override.cape.useCustomApi) {
                String capeIndexDisplay = override.cape.apiIndex == -1
                        ? Component.translatable("wskinloader.config.override.all").getString()
                        : String.valueOf(override.cape.apiIndex);
                Component capeIdxLabel = Component.translatable("wskinloader.config.override.api_index", capeIndexDisplay);
                int cprevBtnX = leftX + font.width(capeIdxLabel) + 8;
                if (isInside(mx, my, cprevBtnX, formY, 16, 16)) {
                    override.cape.apiIndex = Math.max(-1, override.cape.apiIndex - 1);
                    markDirty();
                    return true;
                }
                if (isInside(mx, my, cprevBtnX + 20, formY, 16, 16)) {
                    override.cape.apiIndex = Math.min(editData.capeApis.size() - 1, override.cape.apiIndex + 1);
                    markDirty();
                    return true;
                }
                formY += 20;
            }

            // 完成按钮
            formY += 8;
            Component doneText = Component.translatable("wskinloader.config.button.done");
            int doneW = font.width(doneText) + 16;
            if (isInside(mx, my, leftX, formY, doneW, BUTTON_HEIGHT)) {
                editingPlayerName = null;
                return true;
            }
            return false;
        }

        if (!addingPlayer) {
            // Add button
            Component addPlayerText = Component.translatable("wskinloader.config.button.add_player");
            int addBtnW = font.width(addPlayerText) + 16;
            if (isInside(mx, my, leftX, startY, addBtnW, BUTTON_HEIGHT)) {
                addingPlayer = true;
                recreateFields();
                return true;
            }
            startY += BUTTON_HEIGHT + 12;

            // List item clicks (edit + delete)
            int itemY = startY - scrollOffset;
            List<String> playerNames = new ArrayList<>(editData.playerOverrides.keySet());
            for (int i = 0; i < playerNames.size(); i++) {
                int rowH = 32;
                // Edit button
                int editBtnX = leftX + contentW - 32 - 80;
                int editBtnY = itemY + 8;
                if (isInside(mx, my, editBtnX, editBtnY, 34, 16)) {
                    editingPlayerName = playerNames.get(i);
                    return true;
                }
                // Delete button
                int delBtnX = editBtnX + 38;
                if (isInside(mx, my, delBtnX, editBtnY, 34, 16)) {
                    editData.playerOverrides.remove(playerNames.get(i));
                    markDirty();
                    return true;
                }
                itemY += rowH;
            }
        } else {
            // Form confirm/cancel
            Component confirmText = Component.translatable("wskinloader.config.button.confirm");
            Component cancelFormText = Component.translatable("wskinloader.config.button.cancel");
            int formY = contentY + 30 + 16 + 16 + 28 + 8;
            int confirmW = font.width(confirmText) + 16;
            int cancelFormW = font.width(cancelFormText) + 16;

            if (isInside(mx, my, leftX, formY, confirmW, BUTTON_HEIGHT)) {
                String name = playerNameField != null ? playerNameField.getValue().trim() : "";
                if (!name.isEmpty() && !editData.playerOverrides.containsKey(name)) {
                    editData.playerOverrides.put(name, new PlayerOverride());
                    markDirty();
                }
                addingPlayer = false;
                recreateFields();
                return true;
            }
            if (isInside(mx, my, leftX + confirmW + 8, formY, cancelFormW, BUTTON_HEIGHT)) {
                addingPlayer = false;
                recreateFields();
                return true;
            }
        }
        return false;
    }

    private boolean handleCacheTabClick(int mx, int my) {
        int startY = contentY + 36 + 16 + 24;
        int leftX = contentX + 16;
        Component clearText = Component.translatable("wskinloader.config.cache.clear_all");
        int clearW = font.width(clearText) + 16;

        // Clear all cache button
        if (isInside(mx, my, leftX, startY, clearW, BUTTON_HEIGHT)) {
            SkinCache.clear();
            PlayerSkinSourceCache.clear();
            PlayerNameCache.clear();
            return true;
        }

        // Player list buttons (preview + delete)
        startY += BUTTON_HEIGHT + 12 + 1 + 12 + 16; // skip to list start
        List<UUID> cachedPlayers = new ArrayList<>(SkinCache.getCachedPlayers());
        int itemY = startY - scrollOffset;
        for (int i = 0; i < cachedPlayers.size(); i++) {
            UUID uuid = cachedPlayers.get(i);
            int rowH = 28;

            // Preview button
            int previewBtnX = leftX + contentW - 32 - 120;
            int btnY = itemY + 6;
            if (isInside(mx, my, previewBtnX, btnY, 40, 16)) {
                // Open skin preview
                String name = PlayerNameCache.getName(uuid);
                if (name == null) name = uuid.toString().substring(0, 8);
                if (minecraft != null) {
                    minecraft.setScreenAndShow(new SkinPreviewScreen(this, name, uuid, SkinCache.getSkin(uuid)));
                }
                return true;
            }

            // Delete button
            int delBtnX = previewBtnX + 44;
            if (isInside(mx, my, delBtnX, btnY, 34, 16)) {
                SkinCache.removePlayer(uuid);
                PlayerSkinSourceCache.remove(uuid);
                PlayerNameCache.remove(uuid);
                return true;
            }

            itemY += rowH;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= contentX && mouseX < contentX + contentW && mouseY >= contentY) {
            scrollOffset = Math.max(0, scrollOffset - (int)(scrollY * 12));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        // ESC to close (256 = GLFW_KEY_ESCAPE)
        if (keyEvent.key() == 256) {
            onCancel();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════════

    private void onSave() {
        String error = editData.validate();
        if (error != null) {
            return;
        }
        ModConfig.getInstance().applyFrom(editData);
        ModConfig.getInstance().save();
        
        // 保存后清除所有皮肤缓存，让玩家覆盖配置立即生效
        SkinCache.clear();
        PlayerSkinSourceCache.clear();
        
        dirty = false;
        minecraft.setScreenAndShow(parent);
    }

    private void onCancel() {
        minecraft.setScreenAndShow(parent);
    }

    private void markDirty() {
        dirty = true;
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════

    private void drawButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Component text, int bgColor, int textColor, boolean hovered) {
        int bg = hovered ? lighten(bgColor) : bgColor;
        ctx.fill(x, y, x + w, y + h, bg);
        drawOutline(ctx, x, y, w, h, BORDER);
        // Centered text
        int textWidth = font.width(text);
        int textX = x + (w - textWidth) / 2;
        int textY = y + (h - 9) / 2;
        ctx.text(font, text, textX, textY, textColor, false);
    }

    private void drawOutline(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);         // top
        ctx.fill(x, y + h - 1, x + w, y + h, color); // bottom
        ctx.fill(x, y, x + 1, y + h, color);         // left
        ctx.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    private static int lighten(int color) {
        int a = (color >> 24) & 0xFF;
        int r = Math.min(255, ((color >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 20);
        int b = Math.min(255, (color & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
