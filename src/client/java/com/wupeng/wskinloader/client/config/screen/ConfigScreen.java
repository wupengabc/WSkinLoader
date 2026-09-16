package com.wupeng.wskinloader.client.config.screen;

import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.config.ModConfig.ApiConfig;
import com.wupeng.wskinloader.client.config.ModConfig.ConfigData;
import com.wupeng.wskinloader.client.config.ModConfig.PlayerOverride;
import com.wupeng.wskinloader.client.render.RenderBackendInfo;
import com.wupeng.wskinloader.client.skin.PlayerNameCache;
import com.wupeng.wskinloader.client.skin.PlayerSkinSourceCache;
import com.wupeng.wskinloader.client.skin.SkinCache;
import com.wupeng.wskinloader.client.skin.SkinLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * WSkinLoader config screen.
 *
 * <p>Structure follows the common "options panel" layout: a scrollable category
 * list on the left, and on the right one option per row with the label on the
 * left and its control on the right, plus a header and a footer with the action
 * buttons.
 *
 * <p>Only vanilla components are used (Button, CycleButton, EditBox,
 * StringWidget, ObjectSelectionList, ContainerObjectSelectionList,
 * HeaderAndFooterLayout) so resource packs and UI add-ons can restyle it just
 * like the vanilla options screens.
 */
public class ConfigScreen extends Screen {

    private static final String[] TAB_KEYS = {
            "wskinloader.config.tab.general",
            "wskinloader.config.tab.skin_api",
            "wskinloader.config.tab.cape_api",
            "wskinloader.config.tab.player_override",
            "wskinloader.config.tab.cache"
    };

    private static final String FEEDBACK_URL = "https://github.com/wupengabc/wskinloader/issues";

    private static final int SIDEBAR_WIDTH = 120;
    private static final int ROW_HEIGHT = 26;
    private static final int CONTROL_WIDTH = 150;
    private static final int BUTTON_WIDTH = 54;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private final Screen parent;
    private ConfigData editData;
    private boolean dirty = false;
    private String validationError;
    private int selectedTab = 0;

    // Form state
    private boolean addingApi = false;
    private int editingApiIndex = -1;
    private boolean addingPlayer = false;
    private String editingPlayerName = null;
    private boolean settingMapping = false;
    private boolean editingMappingIsSkin = true;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private @Nullable PageList pageList;

    /** Widgets of the current page; repositioned on every resize. */
    private final List<AbstractWidget> contentWidgets = new ArrayList<>();
    private Runnable contentLayout = () -> {
    };

    public ConfigScreen(Screen parent) {
        super(Component.translatable("wskinloader.config.title"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------
    // LIFECYCLE
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        if (this.editData == null) {
            this.editData = ModConfig.getInstance().deepCopy();
        }

        this.contentWidgets.clear();
        this.contentLayout = () -> {
        };

        // init() runs again after rebuildWidgets() (window resize), so drop the
        // previous header/footer children before re-adding them.
        this.layout.removeChildren();

        this.layout.addTitleHeader(this.title, this.font);

        LinearLayout footer = LinearLayout.horizontal().spacing(8);
        footer.addChild(Button.builder(Component.translatable("wskinloader.config.button.cancel"),
                button -> this.requestClose()).width(100).build());
        footer.addChild(Button.builder(Component.translatable("wskinloader.config.button.save"),
                button -> this.onSave()).width(100).build());
        this.layout.addToFooter(footer);

        this.layout.visitWidgets(this::addRenderableWidget);

        this.repositionElements();

        this.pageList = new PageList(this.minecraft, SIDEBAR_WIDTH, this.sidebarHeight(), this.sidebarTop(), 22);
        this.pageList.setSelectedIndex(this.selectedTab);
        this.addRenderableWidget(this.pageList);

        this.buildContent();
        this.repositionElements();
    }

    // ------------------------------------------------------------------
    // GEOMETRY
    // ------------------------------------------------------------------

    private int sidebarTop() {
        return this.layout.getHeaderHeight() + 10;
    }

    private int sidebarHeight() {
        return Math.max(40, this.height - this.sidebarTop() - this.layout.getFooterHeight() - 10);
    }

    private int contentTop() {
        return this.layout.getHeaderHeight() + 10;
    }

    private int contentBottom() {
        return this.height - this.layout.getFooterHeight() - 10;
    }

    /** Left edge of the sidebar so the whole sidebar+content block is centered. */
    private int blockLeft() {
        return Math.max(10, (this.width - (SIDEBAR_WIDTH + 14 + this.contentWidth())) / 2);
    }

    private int sidebarLeft() {
        return this.blockLeft();
    }

    private int contentLeft() {
        return this.blockLeft() + SIDEBAR_WIDTH + 14;
    }

    private int contentWidth() {
        return Math.max(160, Math.min(420, this.width - 40 - SIDEBAR_WIDTH - 14));
    }

    /**
     * Called by vanilla on init and on every window resize. Rebuilding here is the
     * reliable path: widgets are recreated from the current screen size, so list
     * bounds, row widths and positions can never lag behind the window. Editing
     * state (editData, selected tab, open form) lives in fields and is preserved.
     */
    @Override
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        this.rebuildWidgets();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.pageList != null) {
            this.pageList.updateSizeAndPosition(SIDEBAR_WIDTH, this.sidebarHeight(), this.sidebarLeft(), this.sidebarTop());
        }
        this.contentLayout.run();
    }

    private void addContentWidget(AbstractWidget widget) {
        this.contentWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= TAB_KEYS.length) {
            return;
        }
        this.selectedTab = index;
        this.resetFormState();
        if (this.pageList != null) {
            this.pageList.setSelectedIndex(index);
        }
        this.refreshContent();
    }

    private void resetFormState() {
        this.addingApi = false;
        this.editingApiIndex = -1;
        this.addingPlayer = false;
        this.editingPlayerName = null;
        this.settingMapping = false;
        this.editingMappingIsSkin = true;
    }

    private void markDirty() {
        this.dirty = true;
        this.validationError = null;
    }

    /** Rebuilds the current page in place. */
    private void refreshContent() {
        for (AbstractWidget widget : this.contentWidgets) {
            this.removeWidget(widget);
        }
        this.contentWidgets.clear();
        this.buildContent();
        this.repositionElements();
    }

    // ------------------------------------------------------------------
    // CONTENT
    // ------------------------------------------------------------------

    private void buildContent() {
        switch (this.selectedTab) {
            case 0 -> this.buildGeneralTab();
            case 1, 2 -> this.buildApiTab();
            case 3 -> this.buildPlayerTab();
            case 4 -> this.buildCacheTab();
        }
    }

    /**
     * StringWidget sizes itself from the message when given the (width, height)
     * constructor, but a zero width makes it clip the text to "...". Build it
     * from the message so the label always renders in full.
     */
    private StringWidget label(Component text) {
        return new StringWidget(text, this.font);
    }

    private RowList newRowList() {
        RowList list = new RowList(this.minecraft, this.contentWidth(), 100, 0, ROW_HEIGHT);
        list.setContentWidth(this.contentWidth());
        this.addContentWidget(list);
        return list;
    }

    /** Positions a row list leaving room for an action button above the footer. */
    private void placeRows(RowList list, int topOffset) {
        int top = this.contentTop() + topOffset;
        int height = Math.max(20, this.contentBottom() - BUTTON_HEIGHT - GAP - top);
        // Keep the row width in sync so the rows follow window resizes.
        list.setContentWidth(this.contentWidth());
        list.updateSizeAndPosition(this.contentWidth(), height, this.contentLeft(), top);
    }

    /** Positions a row list using the full content height. */
    private void placeRowsFull(RowList list, int topOffset) {
        int top = this.contentTop() + topOffset;
        int height = Math.max(20, this.contentBottom() - top);
        list.setContentWidth(this.contentWidth());
        list.updateSizeAndPosition(this.contentWidth(), height, this.contentLeft(), top);
    }

    // -- Tab 0: General -------------------------------------------------

    private void buildGeneralTab() {
        RowList list = this.newRowList();

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.general.enable_tab_heads"), null,
                List.of(CycleButton.onOffBuilder(this.editData.enableTabListHeads)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            this.editData.enableTabListHeads = value;
                            this.markDirty();
                        }))));

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.general.enable_nametag_label"), null,
                List.of(CycleButton.onOffBuilder(this.editData.enableNameTagLabel)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            this.editData.enableNameTagLabel = value;
                            this.markDirty();
                        }))));

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.general.render_api",
                        RenderBackendInfo.currentApiName()),
                Component.translatable("wskinloader.config.general.render_api.desc"),
                List.of()));

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.info.feedback"), null,
                List.of(Button.builder(Component.translatable("wskinloader.config.button.open"),
                        button -> com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(FEEDBACK_URL)))
                        .size(BUTTON_WIDTH + 16, BUTTON_HEIGHT).build())));

        this.contentLayout = () -> this.placeRows(list, 0);
    }

    // -- Tab 1/2: Skin & Cape APIs --------------------------------------

    private List<ApiConfig> activeApis() {
        return this.selectedTab == 1 ? this.editData.skinApis : this.editData.capeApis;
    }

    private void buildApiTab() {
        if (this.addingApi || this.editingApiIndex >= 0) {
            this.buildApiForm();
        } else {
            this.buildApiList();
        }
    }

    private void buildApiList() {
        List<ApiConfig> apis = this.activeApis();

        // Registered before the list: vanilla child dispatch returns the first hit
        // widget, so an overlapping list would swallow the button's clicks.
        Button addButton = Button.builder(Component.translatable("wskinloader.config.button.add_api"), button -> {
            this.addingApi = true;
            this.refreshContent();
        }).width(120).build();
        this.addContentWidget(addButton);

        if (apis.isEmpty()) {
            StringWidget empty = this.label(Component.translatable("wskinloader.config.api.empty"));
            this.addContentWidget(empty);
            this.contentLayout = () -> {
                addButton.setPosition(this.contentLeft(), this.contentBottom() - BUTTON_HEIGHT - GAP);
                empty.setPosition(this.contentLeft() + 6, this.contentTop() + 6);
            };
            return;
        }

        RowList list = this.newRowList();

        list.addRow(new RowList.Row(Component.translatable("wskinloader.config.hint.url"), null, List.of()));

        for (int i = 0; i < apis.size(); i++) {
            ApiConfig api = apis.get(i);
            String alias = api.alias != null && !api.alias.isEmpty() ? api.alias
                    : Component.translatable("wskinloader.config.api.no_alias").getString();
            int index = i;
            list.addRow(new RowList.Row(
                    Component.literal(alias),
                    Component.literal(api.url != null ? api.url : ""),
                    List.of(
                            Button.builder(Component.translatable("wskinloader.config.button.edit"), button -> {
                                this.editingApiIndex = index;
                                this.refreshContent();
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build(),
                            Button.builder(Component.translatable("wskinloader.config.button.delete"), button -> {
                                this.adjustApiIndexes(apis == this.editData.skinApis, index);
                                apis.remove(index);
                                this.markDirty();
                                this.refreshContent();
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build())));
        }

        this.contentLayout = () -> {
            addButton.setPosition(this.contentLeft(), this.contentBottom() - BUTTON_HEIGHT - GAP);
            this.placeRows(list, 0);
        };
    }

    private void buildApiForm() {
        List<ApiConfig> apis = this.activeApis();
        String typeKey = this.selectedTab == 1 ? "wskinloader.config.tab.skin_api" : "wskinloader.config.tab.cape_api";
        Component title = this.addingApi
                ? Component.translatable("wskinloader.config.api.add_title", Component.translatable(typeKey))
                : Component.translatable("wskinloader.config.api.edit_title", Component.translatable(typeKey));

        StringWidget titleWidget = this.label(title);
        this.addContentWidget(titleWidget);

        StringWidget aliasLabel = this.label(Component.translatable("wskinloader.config.api.alias_label"));
        this.addContentWidget(aliasLabel);

        EditBox aliasField = new EditBox(this.font, 0, 0, this.contentWidth(), BUTTON_HEIGHT,
                Component.translatable("wskinloader.config.api.alias"));
        aliasField.setMaxLength(64);
        this.addContentWidget(aliasField);

        StringWidget urlLabel = this.label(Component.translatable("wskinloader.config.api.url_label"));
        this.addContentWidget(urlLabel);

        EditBox urlField = new EditBox(this.font, 0, 0, this.contentWidth(), BUTTON_HEIGHT,
                Component.translatable("wskinloader.config.api.url"));
        urlField.setMaxLength(512);
        this.addContentWidget(urlField);

        if (this.editingApiIndex >= 0 && this.editingApiIndex < apis.size()) {
            ApiConfig api = apis.get(this.editingApiIndex);
            aliasField.setValue(api.alias != null ? api.alias : "");
            urlField.setValue(api.url != null ? api.url : "");
        }

        Button confirm = Button.builder(Component.translatable("wskinloader.config.button.confirm"), button -> {
            String url = urlField.getValue().trim();
            if (!url.isEmpty()) {
                String alias = aliasField.getValue().trim();
                if (alias.isEmpty()) {
                    alias = Component.translatable("wskinloader.config.api.no_alias").getString();
                }
                if (this.addingApi) {
                    apis.add(new ApiConfig(url, alias));
                } else if (this.editingApiIndex >= 0 && this.editingApiIndex < apis.size()) {
                    apis.get(this.editingApiIndex).url = url;
                    apis.get(this.editingApiIndex).alias = alias;
                }
                this.markDirty();
            }
            this.resetFormState();
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(confirm);

        Button cancel = Button.builder(Component.translatable("wskinloader.config.button.cancel"), button -> {
            this.resetFormState();
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(cancel);

        this.setInitialFocus(aliasField);

        this.contentLayout = () -> {
            int x = this.contentLeft();
            int y = this.contentTop();
            titleWidget.setPosition(x, y);
            y += 20;
            aliasLabel.setPosition(x, y);
            y += 12;
            aliasField.setPosition(x, y);
            aliasField.setWidth(this.contentWidth());
            y += BUTTON_HEIGHT + GAP;
            urlLabel.setPosition(x, y);
            y += 12;
            urlField.setPosition(x, y);
            urlField.setWidth(this.contentWidth());
            y += BUTTON_HEIGHT + GAP + 4;
            confirm.setPosition(x, y);
            cancel.setPosition(x + 104, y);
        };
    }

    // -- Tab 3: Player overrides ----------------------------------------

    private void buildPlayerTab() {
        if (this.editingPlayerName != null && this.settingMapping) {
            this.buildMappingForm();
        } else if (this.editingPlayerName != null) {
            this.buildPlayerEditForm();
        } else if (this.addingPlayer) {
            this.buildPlayerAddForm();
        } else {
            this.buildPlayerList();
        }
    }

    /** 设置玩家映射来源的子表单。 */
    private void buildMappingForm() {
        PlayerOverride override = this.editData.playerOverrides.get(this.editingPlayerName);
        if (override == null) {
            this.editingPlayerName = null;
            this.settingMapping = false;
            this.buildPlayerList();
            return;
        }

        boolean skin = this.editingMappingIsSkin;
        String current = skin ? override.skinSourcePlayer : override.capeSourcePlayer;

        StringWidget title = this.label(Component.translatable(skin
                ? "wskinloader.config.override.mapping_skin_title"
                : "wskinloader.config.override.mapping_cape_title", this.editingPlayerName));
        this.addContentWidget(title);

        StringWidget hint = this.label(Component.translatable("wskinloader.config.override.mapping_hint"));
        this.addContentWidget(hint);

        StringWidget nameLabel = this.label(Component.translatable("wskinloader.config.override.mapping_source_label"));
        this.addContentWidget(nameLabel);

        EditBox nameField = new EditBox(this.font, 0, 0, this.contentWidth(), BUTTON_HEIGHT,
                Component.translatable("wskinloader.config.override.mapping_source_label"));
        nameField.setMaxLength(32);
        nameField.setValue(current == null ? "" : current);
        this.addContentWidget(nameField);

        Button confirm = Button.builder(Component.translatable("wskinloader.config.button.confirm"), button -> {
            String value = nameField.getValue().trim();
            if (skin) {
                override.skinSourcePlayer = value;
            } else {
                override.capeSourcePlayer = value;
            }
            this.markDirty();
            this.settingMapping = false;
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(confirm);

        Button cancel = Button.builder(Component.translatable("wskinloader.config.button.cancel"), button -> {
            this.settingMapping = false;
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(cancel);

        this.setInitialFocus(nameField);

        this.contentLayout = () -> {
            int x = this.contentLeft();
            int y = this.contentTop();
            title.setPosition(x, y);
            y += 20;
            hint.setPosition(x, y);
            y += 16;
            nameLabel.setPosition(x, y);
            y += 12;
            nameField.setPosition(x, y);
            nameField.setWidth(this.contentWidth());
            y += BUTTON_HEIGHT + GAP + 4;
            confirm.setPosition(x, y);
            cancel.setPosition(x + 104, y);
        };
    }

    private void buildPlayerList() {
        Button addButton = Button.builder(Component.translatable("wskinloader.config.button.add_player"), button -> {
            this.addingPlayer = true;
            this.refreshContent();
        }).width(160).build();
        this.addContentWidget(addButton);

        List<String> playerNames = new ArrayList<>(this.editData.playerOverrides.keySet());

        if (playerNames.isEmpty()) {
            StringWidget empty = this.label(Component.translatable("wskinloader.config.override.empty"));
            this.addContentWidget(empty);
            this.contentLayout = () -> {
                addButton.setPosition(this.contentLeft(), this.contentBottom() - BUTTON_HEIGHT - GAP);
                empty.setPosition(this.contentLeft() + 6, this.contentTop() + 6);
            };
            return;
        }

        RowList list = this.newRowList();

        list.addRow(new RowList.Row(Component.translatable("wskinloader.config.hint.player_override"), null, List.of()));

        for (String name : playerNames) {
            PlayerOverride override = this.editData.playerOverrides.get(name);
            if (override == null) {
                continue;
            }
            String skipStr = override.skipPremiumCheck
                    ? Component.translatable("wskinloader.config.override.yes").getString()
                    : Component.translatable("wskinloader.config.override.no").getString();
            String skinStr = override.skin.useCustomApi
                    ? Component.translatable("wskinloader.config.override.custom").getString()
                    : Component.translatable("wskinloader.config.override.premium").getString();
            String capeStr = override.cape.useCustomApi
                    ? Component.translatable("wskinloader.config.override.custom").getString()
                    : Component.translatable("wskinloader.config.override.premium").getString();

            String mappingStr = "";
            if (!override.skinSourcePlayer.isBlank()) {
                mappingStr = Component.translatable("wskinloader.config.override.mapping_short",
                        override.skinSourcePlayer).getString();
            }
            if (!override.capeSourcePlayer.isBlank()) {
                mappingStr += (mappingStr.isEmpty() ? "" : "  ")
                        + Component.translatable("wskinloader.config.override.mapping_cape_short",
                        override.capeSourcePlayer).getString();
            }
            final String summaryMapping = mappingStr;

            list.addRow(new RowList.Row(
                    Component.literal(name),
                    summaryMapping.isEmpty()
                            ? Component.translatable("wskinloader.config.override.info_summary", skipStr, skinStr, capeStr)
                            : Component.translatable("wskinloader.config.override.info_summary_mapped",
                            skipStr, skinStr, capeStr, summaryMapping),
                    List.of(
                            Button.builder(Component.translatable("wskinloader.config.button.edit"), button -> {
                                this.editingPlayerName = name;
                                this.refreshContent();
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build(),
                            Button.builder(Component.translatable("wskinloader.config.button.delete"), button -> {
                                this.editData.playerOverrides.remove(name);
                                this.markDirty();
                                this.refreshContent();
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build())));
        }

        this.contentLayout = () -> {
            addButton.setPosition(this.contentLeft(), this.contentBottom() - BUTTON_HEIGHT - GAP);
            this.placeRows(list, 0);
        };
    }

    private void buildPlayerAddForm() {
        StringWidget title = this.label(Component.translatable("wskinloader.config.override.add_title"));
        this.addContentWidget(title);

        StringWidget nameLabel = this.label(Component.translatable("wskinloader.config.override.name_label"));
        this.addContentWidget(nameLabel);

        EditBox nameField = new EditBox(this.font, 0, 0, this.contentWidth(), BUTTON_HEIGHT,
                Component.translatable("wskinloader.config.override.name_label"));
        nameField.setMaxLength(32);
        this.addContentWidget(nameField);

        Button confirm = Button.builder(Component.translatable("wskinloader.config.button.confirm"), button -> {
            String name = nameField.getValue().trim();
            if (!name.isEmpty() && !this.editData.playerOverrides.containsKey(name)) {
                this.editData.playerOverrides.put(name, new PlayerOverride());
                this.markDirty();
            }
            this.resetFormState();
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(confirm);

        Button cancel = Button.builder(Component.translatable("wskinloader.config.button.cancel"), button -> {
            this.resetFormState();
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(cancel);

        this.setInitialFocus(nameField);

        this.contentLayout = () -> {
            int x = this.contentLeft();
            int y = this.contentTop();
            title.setPosition(x, y);
            y += 20;
            nameLabel.setPosition(x, y);
            y += 12;
            nameField.setPosition(x, y);
            nameField.setWidth(this.contentWidth());
            y += BUTTON_HEIGHT + GAP + 4;
            confirm.setPosition(x, y);
            cancel.setPosition(x + 104, y);
        };
    }

    private void buildPlayerEditForm() {
        PlayerOverride override = this.editData.playerOverrides.get(this.editingPlayerName);
        if (override == null) {
            this.editingPlayerName = null;
            this.buildPlayerList();
            return;
        }

        RowList list = this.newRowList();

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.edit_title", this.editingPlayerName), null, List.of()));

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.skip_premium"), null,
                List.of(CycleButton.onOffBuilder(override.skipPremiumCheck)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            override.skipPremiumCheck = value;
                            this.markDirty();
                        }))));

        // 玩家映射：用另一个玩家的皮肤/披风覆盖当前玩家
        list.addRow(new RowList.Row(Component.translatable("wskinloader.config.override.mapping_section"), null, List.of()));
        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.mapping_skin"),
                override.skinSourcePlayer.isBlank()
                        ? Component.translatable("wskinloader.config.override.mapping_self")
                        : Component.literal(override.skinSourcePlayer),
                List.of(
                        Button.builder(Component.translatable("wskinloader.config.button.set"), button -> {
                            this.editingMappingIsSkin = true;
                            this.settingMapping = true;
                            this.refreshContent();
                        }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build(),
                        Button.builder(Component.translatable("wskinloader.config.button.clear"), button -> {
                            override.skinSourcePlayer = "";
                            this.markDirty();
                            this.refreshContent();
                        }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build())));
        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.mapping_cape"),
                override.capeSourcePlayer.isBlank()
                        ? Component.translatable("wskinloader.config.override.mapping_self")
                        : Component.literal(override.capeSourcePlayer),
                List.of(
                        Button.builder(Component.translatable("wskinloader.config.button.set"), button -> {
                            this.editingMappingIsSkin = false;
                            this.settingMapping = true;
                            this.refreshContent();
                        }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build(),
                        Button.builder(Component.translatable("wskinloader.config.button.clear"), button -> {
                            override.capeSourcePlayer = "";
                            this.markDirty();
                            this.refreshContent();
                        }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build())));

        List<String> modelValues = List.of("auto", "slim", "wide");
        String currentModel = modelValues.contains(override.modelType) ? override.modelType : "auto";
        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.model_type"), null,
                List.of(new CycleButton.Builder<String>(value -> Component.translatable(value.equals("auto")
                        ? "wskinloader.config.override.model_auto"
                        : value.equals("slim")
                        ? "wskinloader.config.override.model_slim"
                        : "wskinloader.config.override.model_wide"), () -> currentModel)
                        .withValues(modelValues)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            override.modelType = value;
                            this.markDirty();
                        }))));

        // Skin
        list.addRow(new RowList.Row(Component.translatable("wskinloader.config.override.skin"), null, List.of()));
        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.use_custom_api"), null,
                List.of(CycleButton.onOffBuilder(override.skin.useCustomApi)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            override.skin.useCustomApi = value;
                            this.markDirty();
                            this.refreshContent();
                        }))));
        if (override.skin.useCustomApi) {
            list.addRow(this.apiIndexRow(override.skin, this.editData.skinApis));
        }

        // Cape
        list.addRow(new RowList.Row(Component.translatable("wskinloader.config.override.cape"), null, List.of()));
        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.override.use_custom_api"), null,
                List.of(CycleButton.onOffBuilder(override.cape.useCustomApi)
                        .displayOnlyValue()
                        .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (button, value) -> {
                            override.cape.useCustomApi = value;
                            this.markDirty();
                            this.refreshContent();
                        }))));
        if (override.cape.useCustomApi) {
            list.addRow(this.apiIndexRow(override.cape, this.editData.capeApis));
        }

        Button done = Button.builder(Component.translatable("wskinloader.config.button.done"), button -> {
            this.resetFormState();
            this.refreshContent();
        }).width(100).build();
        this.addContentWidget(done);

        this.contentLayout = () -> {
            this.placeRows(list, 0);
            done.setPosition(this.contentLeft(), this.contentBottom() - BUTTON_HEIGHT - GAP);
        };
    }

    private RowList.Row apiIndexRow(ModConfig.TextureOverride texture, List<ApiConfig> apis) {
        List<String> values = new ArrayList<>();
        String allLabel = Component.translatable("wskinloader.config.override.all").getString();
        values.add(allLabel);
        for (int i = 0; i < apis.size(); i++) {
            values.add(String.valueOf(i));
        }
        int clamped = texture.apiIndex >= -1 && texture.apiIndex < apis.size() ? texture.apiIndex : -1;
        String current = clamped == -1 ? allLabel : String.valueOf(clamped);

        CycleButton<String> button = new CycleButton.Builder<String>(
                value -> value.equals(allLabel)
                        ? Component.translatable("wskinloader.config.override.all")
                        : Component.literal(value),
                () -> current)
                .withValues(values)
                .displayOnlyValue()
                .create(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, Component.empty(), (btn, value) -> {
                    texture.apiIndex = value.equals(allLabel) ? -1 : Integer.parseInt(value);
                    this.markDirty();
                });

        return new RowList.Row(Component.translatable("wskinloader.config.override.api_index_short"), null, List.of(button));
    }

    // -- Tab 4: Cache ---------------------------------------------------

    private void buildCacheTab() {
        List<UUID> cachedPlayers = new ArrayList<>(SkinCache.getCachedPlayers());

        RowList list = this.newRowList();

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.cache.stats"), null,
                List.of(Button.builder(Component.translatable("wskinloader.config.cache.clear_all"), button -> {
                    SkinLoader.invalidateAll();
                    this.refreshContent();
                }).size(140, BUTTON_HEIGHT).build())));

        list.addRow(new RowList.Row(
                Component.translatable("wskinloader.config.cache.count", cachedPlayers.size()), null, List.of()));

        if (cachedPlayers.isEmpty()) {
            list.addRow(new RowList.Row(Component.translatable("wskinloader.config.cache.empty"), null, List.of()));
            this.contentLayout = () -> this.placeRows(list, 0);
            return;
        }

        for (UUID uuid : cachedPlayers) {
            String resolvedName = PlayerNameCache.getName(uuid);
            if (resolvedName == null) {
                resolvedName = uuid.toString().substring(0, 8) + "...";
            }
            final String displayName = resolvedName;
            String resolvedSource = PlayerSkinSourceCache.getSource(uuid);
            if (resolvedSource == null) {
                resolvedSource = "unknown";
            }
            final String displaySource = resolvedSource;
            final UUID playerId = uuid;

            list.addRow(new RowList.Row(
                    Component.literal(displayName),
                    Component.literal("[" + displaySource + "] " + uuid.toString().substring(0, 13) + "..."),
                    List.of(
                            Button.builder(Component.translatable("wskinloader.config.button.preview"), button -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreenAndShow(new SkinPreviewScreen(this, displayName, playerId,
                                            SkinCache.getSkin(playerId)));
                                }
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build(),
                            Button.builder(Component.translatable("wskinloader.config.button.delete"), button -> {
                                // Drop the cached textures and start loading again
                                // right away; vanilla will not re-resolve a skin
                                // it has already memoized.
                                SkinLoader.reload(playerId);
                                this.refreshContent();
                            }).size(BUTTON_WIDTH, BUTTON_HEIGHT).build())));
        }

        this.contentLayout = () -> this.placeRows(list, 0);
    }

    // ------------------------------------------------------------------
    // RENDERING
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Translucent panels behind the sidebar and the content column, so the
        // screen reads as a single panel while the widgets keep vanilla styling.
        int top = this.sidebarTop() - 4;
        int bottom = this.height - this.layout.getFooterHeight() - 6;
        this.drawPanel(ctx, this.sidebarLeft() - 4, top, SIDEBAR_WIDTH + 8, bottom - top);
        this.drawPanel(ctx, this.contentLeft() - 6, top, this.contentWidth() + 12, bottom - top);

        super.extractRenderState(ctx, mouseX, mouseY, delta);

        if (this.validationError != null) {
            ctx.centeredText(this.font, this.validationError, this.width / 2,
                    this.layout.getHeaderHeight() + 2, 0xFFFF5555);
        }
    }

    private void drawPanel(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        ctx.fill(x, y, x + w, y + h, 0x66000000);
        ctx.fill(x, y, x + w, y + 1, 0x33FFFFFF);
        ctx.fill(x, y + h - 1, x + w, y + h, 0x33FFFFFF);
        ctx.fill(x, y, x + 1, y + h, 0x33FFFFFF);
        ctx.fill(x + w - 1, y, x + w, y + h, 0x33FFFFFF);
    }

    // ------------------------------------------------------------------
    // ACTIONS
    // ------------------------------------------------------------------

    private void onSave() {
        String error = this.editData.validate();
        if (error != null) {
            this.validationError = error;
            return;
        }
        this.validationError = null;

        ModConfig live = ModConfig.getInstance();
        // Only invalidate caches when something that actually affects skin
        // resolution changed. Display-only toggles must not wipe the cache.
        boolean skinConfigChanged = !sameApis(live.skinApis, this.editData.skinApis)
                || !sameApis(live.capeApis, this.editData.capeApis)
                || !sameOverrides(live.playerOverrides, this.editData.playerOverrides);

        live.applyFrom(this.editData);
        live.save();

        if (skinConfigChanged) {
            SkinLoader.invalidateAll();
            com.wupeng.wskinloader.client.skin.MojangApiChecker.clearCache();
            com.wupeng.wskinloader.client.skin.MojangSessionApi.clearCache();
        }

        this.dirty = false;
        this.goToParent();
    }

    private static boolean sameApis(List<ApiConfig> a, List<ApiConfig> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            ApiConfig left = a.get(i);
            ApiConfig right = b.get(i);
            String leftAlias = left.alias != null ? left.alias : "";
            String rightAlias = right.alias != null ? right.alias : "";
            String leftUrl = left.url != null ? left.url : "";
            String rightUrl = right.url != null ? right.url : "";
            if (!leftAlias.equals(rightAlias) || !leftUrl.equals(rightUrl)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameOverrides(Map<String, PlayerOverride> a, Map<String, PlayerOverride> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (Map.Entry<String, PlayerOverride> entry : a.entrySet()) {
            PlayerOverride left = entry.getValue();
            PlayerOverride right = b.get(entry.getKey());
            if (right == null
                    || left.skipPremiumCheck != right.skipPremiumCheck
                    || !Objects.equals(left.modelType, right.modelType)
                    || !Objects.equals(left.skinSourcePlayer, right.skinSourcePlayer)
                    || !Objects.equals(left.capeSourcePlayer, right.capeSourcePlayer)
                    || !sameTexture(left.skin, right.skin)
                    || !sameTexture(left.cape, right.cape)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTexture(ModConfig.TextureOverride a, ModConfig.TextureOverride b) {
        return a.useCustomApi == b.useCustomApi && a.apiIndex == b.apiIndex;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        this.requestClose();
    }

    private void requestClose() {
        if (this.dirty && this.minecraft != null) {
            this.minecraft.setScreenAndShow(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            this.goToParent();
                        } else if (this.minecraft != null) {
                            this.minecraft.setScreenAndShow(this);
                        }
                    },
                    Component.translatable("wskinloader.config.title"),
                    Component.translatable("wskinloader.config.confirm_discard"),
                    Component.translatable("wskinloader.config.confirm_discard.yes"),
                    Component.translatable("wskinloader.config.confirm_discard.no")));
        } else {
            this.goToParent();
        }
    }

    private void goToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreenAndShow(this.parent);
        }
    }

    private void adjustApiIndexes(boolean skin, int removedIndex) {
        for (PlayerOverride override : this.editData.playerOverrides.values()) {
            ModConfig.TextureOverride texture = skin ? override.skin : override.cape;
            if (texture.apiIndex == removedIndex) {
                texture.apiIndex = -1;
            } else if (texture.apiIndex > removedIndex) {
                texture.apiIndex--;
            }
        }
    }

    // ------------------------------------------------------------------
    // LEFT CATEGORY LIST (vanilla ObjectSelectionList)
    // ------------------------------------------------------------------

    private class PageList extends ObjectSelectionList<PageList.PageEntry> {

        PageList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;
            for (int i = 0; i < TAB_KEYS.length; i++) {
                this.addEntry(new PageEntry(i, Component.translatable(TAB_KEYS[i])));
            }
        }

        void setSelectedIndex(int index) {
            if (index >= 0 && index < this.children().size()) {
                this.setSelected(this.children().get(index));
            }
        }

        @Override
        public int getRowWidth() {
            return SIDEBAR_WIDTH - 12;
        }

        /**
         * The list keeps its own translucent background so it reads as a second
         * layer inside the screen-drawn panel, with a visible margin on each side.
         * Only the list separators are suppressed: they drew stray lines across
         * the panel that did not line up with it.
         */
        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        }

        @Override
        public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            PageEntry entry = this.getEntryAtPosition(event.x(), event.y());
            if (entry != null) {
                this.setSelected(entry);
                ConfigScreen.this.selectTab(entry.index);
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        class PageEntry extends ObjectSelectionList.Entry<PageEntry> {
            private final int index;
            private final Component label;

            PageEntry(int index, Component label) {
                this.index = index;
                this.label = label;
            }

            @Override
            public Component getNarration() {
                return this.label;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                boolean selected = PageList.this.getSelected() == this;
                int color = selected ? 0xFFFFFFFF : (hovered ? 0xFFE8E8F0 : 0xFFB0B0BC);
                int y = this.getContentY() + (this.getContentHeight() - 9) / 2;
                int x = this.getContentX() + 6;
                graphics.text(ConfigScreen.this.font,
                        ConfigScreen.trim(ConfigScreen.this.font, this.label.getString(), this.getContentWidth() - 10),
                        x, y, color);
                if (selected) {
                    graphics.fill(this.getContentX(), this.getContentY() + 2,
                            this.getContentX() + 2, this.getContentY() + this.getContentHeight() - 2, 0xFF2BB673);
                }
            }
        }
    }

    private static String trim(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int limit = maxWidth - font.width(suffix);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) > limit) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    // ------------------------------------------------------------------
    // OPTION ROW LIST (label left, controls right)
    // ------------------------------------------------------------------

    private static class RowList extends ContainerObjectSelectionList<RowList.RowEntry> {

        private int contentWidth;

        RowList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;
        }

        void setContentWidth(int contentWidth) {
            this.contentWidth = contentWidth;
        }

        void addRow(Row row) {
            this.addEntry(new RowEntry(row));
        }

        @Override
        public int getRowWidth() {
            return this.contentWidth;
        }

        /**
         * Keeps the list's own translucent layer (the second layer inside the
         * panel) and suppresses only the separators, which drew stray lines that
         * did not align with the panel edges.
         */
        @Override
        protected void extractListSeparators(GuiGraphicsExtractor graphics) {
        }

        record Row(Component label, @Nullable Component sub, List<AbstractWidget> controls) {
        }

        class RowEntry extends ContainerObjectSelectionList.Entry<RowEntry> {
            private final Row row;

            RowEntry(Row row) {
                this.row = row;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                Font font = RowList.this.minecraft.font;
                List<AbstractWidget> controls = this.row.controls();

                int controlsWidth = 0;
                for (AbstractWidget widget : controls) {
                    controlsWidth += widget.getWidth() + GAP;
                }
                if (controlsWidth > 0) {
                    controlsWidth -= GAP;
                }

                int textX = this.getContentX() + 4;
                int textMax = Math.max(10, this.getContentWidth() - 8 - controlsWidth - GAP);
                boolean hasSub = this.row.sub() != null;
                int lineY = this.getContentY() + (hasSub ? 3 : (this.getContentHeight() - 9) / 2);

                graphics.text(font, trim(font, this.row.label().getString(), textMax), textX, lineY, 0xFFFFFFFF);
                if (hasSub) {
                    graphics.text(font, trim(font, this.row.sub().getString(), textMax),
                            textX, this.getContentY() + 14, 0xFFA0A0B0);
                }

                int controlX = this.getContentX() + this.getContentWidth() - 4 - controlsWidth;
                for (AbstractWidget widget : controls) {
                    widget.setPosition(controlX, this.getContentY() + (this.getContentHeight() - widget.getHeight()) / 2);
                    widget.extractRenderState(graphics, mouseX, mouseY, a);
                    controlX += widget.getWidth() + GAP;
                }
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return this.row.controls();
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return this.row.controls();
            }
        }
    }
}
