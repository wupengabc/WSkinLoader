package com.wupeng.wskinloader.client.config.screen;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * WSkinLoader config screen (Minecraft 1.16.5).
 *
 * <p>Left: a scrollable category list. Right: one option per row with the label
 * on the left and the control on the right. Only vanilla widgets are used.
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

    private static final int SIDEBAR_WIDTH = 110;
    private static final int ROW_HEIGHT = 26;
    private static final int CONTROL_WIDTH = 90;
    private static final int BUTTON_WIDTH = 54;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;

    private final Screen parent;
    private ConfigData editData;
    private boolean dirty = false;
    private String validationError;
    private int selectedTab = 0;

    private boolean addingApi = false;
    private int editingApiIndex = -1;
    private boolean addingPlayer = false;
    private String editingPlayerName = null;
    private boolean settingMapping = false;
    private boolean editingMappingIsSkin = true;

    private PageList pageList;
    private final List<AbstractSelectionList<?>> rowLists = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(new TranslatableComponent("wskinloader.config.title"));
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

        this.rowLists.clear();

        this.pageList = new PageList(this.minecraft, SIDEBAR_WIDTH, this.sidebarHeight(), this.sidebarTop(), 20);
        this.pageList.setLeftPos(this.sidebarLeft());
        this.pageList.setSelectedIndex(this.selectedTab);
        this.addWidget(this.pageList);

        this.buildContent();

        int footerY = this.height - 28;
        this.addButton(new Button(this.width - 210, footerY, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.cancel"), b -> this.requestClose()));
        this.addButton(new Button(this.width - 104, footerY, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.save"), b -> this.onSave()));
    }

    private void refresh() {
        this.init(this.minecraft, this.width, this.height);
    }

    private int sidebarTop() {
        return 30;
    }

    private int sidebarHeight() {
        return Math.max(40, this.height - this.sidebarTop() - 36);
    }

    private int contentTop() {
        return 26;
    }

    private int contentBottom() {
        return this.height - 34;
    }

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
        return Math.max(160, Math.min(360, this.width - 40 - SIDEBAR_WIDTH - 14));
    }

    private void addContentWidget(AbstractWidget widget) {
        this.addButton(widget);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= TAB_KEYS.length) {
            return;
        }
        this.selectedTab = index;
        this.resetFormState();
        this.refresh();
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

    // ------------------------------------------------------------------
    // CONTENT
    // ------------------------------------------------------------------

    private void buildContent() {
        switch (this.selectedTab) {
            case 0: this.buildGeneralTab(); break;
            case 1:
            case 2: this.buildApiTab(); break;
            case 3: this.buildPlayerTab(); break;
            case 4: this.buildCacheTab(); break;
            default: break;
        }
    }

    private RowList newRowList(int topOffset) {
        int top = this.contentTop() + topOffset;
        int w = this.contentWidth();
        RowList list = new RowList(this.minecraft, w, this.contentBottom() - top, top, ROW_HEIGHT);
        list.setLeftPos(this.contentLeft());
        this.rowLists.add(list);
        this.addWidget(list);
        return list;
    }

    // -- Tab 0: General -------------------------------------------------

    private void buildGeneralTab() {
        RowList list = this.newRowList(0);

        list.addRow(booleanRow("wskinloader.config.general.enable_tab_heads", null,
                this.editData.enableTabListHeads, v -> {
                    this.editData.enableTabListHeads = v;
                    this.markDirty();
                    this.refresh();
                }));

        if (this.editData.enableTabListHeads) {
            list.addRow(booleanRow("wskinloader.config.general.skip_duplicate_tab_head",
                    "wskinloader.config.general.skip_duplicate_tab_head.desc",
                    this.editData.skipDuplicateTabHead, v -> {
                        this.editData.skipDuplicateTabHead = v;
                        this.markDirty();
                    }));
        }

        list.addRow(booleanRow("wskinloader.config.general.enable_nametag_label", null,
                this.editData.enableNameTagLabel, v -> {
                    this.editData.enableNameTagLabel = v;
                    this.markDirty();
                }));

        list.addRow(booleanRow("wskinloader.config.general.enable_chat_faces",
                "wskinloader.config.general.enable_chat_faces.desc",
                this.editData.enableChatFaces, v -> {
                    this.editData.enableChatFaces = v;
                    this.markDirty();
                }));

        list.addRow(booleanRow("wskinloader.config.general.premium_first",
                "wskinloader.config.general.premium_first.desc",
                this.editData.premiumFirst, v -> {
                    this.editData.premiumFirst = v;
                    this.markDirty();
                    this.refresh();
                }));

        if (this.editData.premiumFirst) {
            list.addRow(booleanRow("wskinloader.config.general.keep_premium_when_unavailable",
                    "wskinloader.config.general.keep_premium_when_unavailable.desc",
                    this.editData.keepPremiumWhenUnavailable, v -> {
                        this.editData.keepPremiumWhenUnavailable = v;
                        this.markDirty();
                    }));
        }

        list.addRow(new Row(
                new TranslatableComponent("wskinloader.config.general.render_api", RenderBackendInfo.currentApiName()),
                new TranslatableComponent("wskinloader.config.general.render_api.desc"),
                new ArrayList<>()));

        Button open = new Button(0, 0, BUTTON_WIDTH + 16, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.open"),
                b -> net.minecraft.Util.getPlatform().openUri(FEEDBACK_URL));
        list.addRow(new Row(new TranslatableComponent("wskinloader.config.info.feedback"), null,
                new ArrayList<>(java.util.Collections.singletonList(open))));
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

        int addY = this.contentBottom() - BUTTON_HEIGHT;
        this.addContentWidget(new Button(this.contentLeft(), addY, 120, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.add_api"), b -> {
                    this.addingApi = true;
                    this.refresh();
                }));

        if (apis.isEmpty()) {
            this.addContentWidget(new net.minecraft.client.gui.components.AbstractWidget(this.contentLeft() + 6,
                    this.contentTop() + 6, 200, 12, new TranslatableComponent("wskinloader.config.api.empty")) {
                @Override public void render(PoseStack stack, int mouseX, int mouseY, float delta) {
                    drawString(stack, ConfigScreen.this.font, this.getMessage().getString(), this.x, this.y, 0xFFB0B0BC);
                }
            });
            return;
        }

        RowList list = this.newRowList(0);
        list.addRow(new Row(new TranslatableComponent("wskinloader.config.hint.url"), null, new ArrayList<>()));

        for (int i = 0; i < apis.size(); i++) {
            ApiConfig api = apis.get(i);
            String alias = api.alias != null && !api.alias.isEmpty()
                    ? api.alias : new TranslatableComponent("wskinloader.config.api.no_alias").getString();
            int index = i;
            Button edit = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.edit"), b -> {
                        this.editingApiIndex = index;
                        this.refresh();
                    });
            Button delete = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.delete"), b -> {
                        this.adjustApiIndexes(apis == this.editData.skinApis, index);
                        apis.remove(index);
                        this.markDirty();
                        this.refresh();
                    });
            List<AbstractWidget> controls = new ArrayList<>();
            controls.add(edit);
            controls.add(delete);
            list.addRow(new Row(new TextComponent(alias),
                    new TextComponent(api.url != null ? api.url : ""), controls));
        }
    }

    private void buildApiForm() {
        List<ApiConfig> apis = this.activeApis();
        String typeKey = this.selectedTab == 1 ? "wskinloader.config.tab.skin_api" : "wskinloader.config.tab.cape_api";
        Component title = this.addingApi
                ? new TranslatableComponent("wskinloader.config.api.add_title", new TranslatableComponent(typeKey))
                : new TranslatableComponent("wskinloader.config.api.edit_title", new TranslatableComponent(typeKey));

        int x = this.contentLeft();
        int w = this.contentWidth();
        int y = this.contentTop();
        addLabel(title, x, y); y += 20;
        addLabel(new TranslatableComponent("wskinloader.config.api.alias_label"), x, y); y += 12;

        EditBox aliasField = new EditBox(this.font, x, y, w, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.api.alias"));
        aliasField.setMaxLength(64);
        this.addContentWidget(aliasField); y += BUTTON_HEIGHT + GAP;
        addLabel(new TranslatableComponent("wskinloader.config.api.url_label"), x, y); y += 12;

        EditBox urlField = new EditBox(this.font, x, y, w, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.api.url"));
        urlField.setMaxLength(512);
        this.addContentWidget(urlField); y += BUTTON_HEIGHT + GAP + 4;

        if (this.editingApiIndex >= 0 && this.editingApiIndex < apis.size()) {
            ApiConfig api = apis.get(this.editingApiIndex);
            aliasField.setValue(api.alias != null ? api.alias : "");
            urlField.setValue(api.url != null ? api.url : "");
        }

        final int fy = y;
        this.addContentWidget(new Button(x, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.confirm"), b -> {
                    String url = urlField.getValue().trim();
                    if (!url.isEmpty()) {
                        String alias = aliasField.getValue().trim();
                        if (alias.isEmpty()) {
                            alias = new TranslatableComponent("wskinloader.config.api.no_alias").getString();
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
                    this.refresh();
                }));
        this.addContentWidget(new Button(x + 104, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.cancel"), b -> {
                    this.resetFormState();
                    this.refresh();
                }));

        this.setInitialFocus(aliasField);
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

        int x = this.contentLeft();
        int w = this.contentWidth();
        int y = this.contentTop();
        addLabel(new TranslatableComponent(skin
                ? "wskinloader.config.override.mapping_skin_title"
                : "wskinloader.config.override.mapping_cape_title", this.editingPlayerName), x, y); y += 20;
        addLabel(new TranslatableComponent("wskinloader.config.override.mapping_hint"), x, y); y += 16;
        addLabel(new TranslatableComponent("wskinloader.config.override.mapping_source_label"), x, y); y += 12;

        EditBox nameField = new EditBox(this.font, x, y, w, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.override.mapping_source_label"));
        nameField.setMaxLength(32);
        nameField.setValue(current == null ? "" : current);
        this.addContentWidget(nameField); y += BUTTON_HEIGHT + GAP + 4;

        final int fy = y;
        this.addContentWidget(new Button(x, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.confirm"), b -> {
                    String value = nameField.getValue().trim();
                    if (skin) {
                        override.skinSourcePlayer = value;
                    } else {
                        override.capeSourcePlayer = value;
                    }
                    this.markDirty();
                    this.settingMapping = false;
                    this.refresh();
                }));
        this.addContentWidget(new Button(x + 104, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.cancel"), b -> {
                    this.settingMapping = false;
                    this.refresh();
                }));

        this.setInitialFocus(nameField);
    }

    private void buildPlayerList() {
        int addY = this.contentBottom() - BUTTON_HEIGHT;
        this.addContentWidget(new Button(this.contentLeft(), addY, 160, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.add_player"), b -> {
                    this.addingPlayer = true;
                    this.refresh();
                }));

        List<String> playerNames = new ArrayList<>(this.editData.playerOverrides.keySet());
        if (playerNames.isEmpty()) {
            addLabel(new TranslatableComponent("wskinloader.config.override.empty"),
                    this.contentLeft() + 6, this.contentTop() + 6);
            return;
        }

        RowList list = this.newRowList(0);
        list.addRow(new Row(new TranslatableComponent("wskinloader.config.hint.player_override"), null, new ArrayList<>()));

        for (String name : playerNames) {
            PlayerOverride override = this.editData.playerOverrides.get(name);
            if (override == null) {
                continue;
            }
            String skipStr = new TranslatableComponent(override.skipPremiumCheck
                    ? "wskinloader.config.override.yes" : "wskinloader.config.override.no").getString();
            String skinStr = new TranslatableComponent(override.skin.useCustomApi
                    ? "wskinloader.config.override.custom" : "wskinloader.config.override.premium").getString();
            String capeStr = new TranslatableComponent(override.cape.useCustomApi
                    ? "wskinloader.config.override.custom" : "wskinloader.config.override.premium").getString();

            String mappingStr = "";
            if (override.skinSourcePlayer != null && !override.skinSourcePlayer.trim().isEmpty()) {
                mappingStr = new TranslatableComponent("wskinloader.config.override.mapping_short",
                        override.skinSourcePlayer).getString();
            }
            if (override.capeSourcePlayer != null && !override.capeSourcePlayer.trim().isEmpty()) {
                mappingStr += (mappingStr.isEmpty() ? "" : "  ")
                        + new TranslatableComponent("wskinloader.config.override.mapping_cape_short",
                        override.capeSourcePlayer).getString();
            }

            Component sub = mappingStr.isEmpty()
                    ? new TranslatableComponent("wskinloader.config.override.info_summary", skipStr, skinStr, capeStr)
                    : new TranslatableComponent("wskinloader.config.override.info_summary_mapped",
                    skipStr, skinStr, capeStr, mappingStr);

            Button edit = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.edit"), b -> {
                        this.editingPlayerName = name;
                        this.refresh();
                    });
            Button delete = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.delete"), b -> {
                        this.editData.playerOverrides.remove(name);
                        this.markDirty();
                        this.refresh();
                    });
            List<AbstractWidget> controls = new ArrayList<>();
            controls.add(edit);
            controls.add(delete);
            list.addRow(new Row(new TextComponent(name), sub, controls));
        }
    }

    private void buildPlayerAddForm() {
        int x = this.contentLeft();
        int w = this.contentWidth();
        int y = this.contentTop();
        addLabel(new TranslatableComponent("wskinloader.config.override.add_title"), x, y); y += 20;
        addLabel(new TranslatableComponent("wskinloader.config.override.name_label"), x, y); y += 12;

        EditBox nameField = new EditBox(this.font, x, y, w, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.override.name_label"));
        nameField.setMaxLength(32);
        this.addContentWidget(nameField); y += BUTTON_HEIGHT + GAP + 4;

        final int fy = y;
        this.addContentWidget(new Button(x, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.confirm"), b -> {
                    String name = nameField.getValue().trim();
                    if (!name.isEmpty() && !this.editData.playerOverrides.containsKey(name)) {
                        this.editData.playerOverrides.put(name, new PlayerOverride());
                        this.markDirty();
                    }
                    this.resetFormState();
                    this.refresh();
                }));
        this.addContentWidget(new Button(x + 104, fy, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.cancel"), b -> {
                    this.resetFormState();
                    this.refresh();
                }));

        this.setInitialFocus(nameField);
    }

    private void buildPlayerEditForm() {
        PlayerOverride override = this.editData.playerOverrides.get(this.editingPlayerName);
        if (override == null) {
            this.editingPlayerName = null;
            this.buildPlayerList();
            return;
        }

        RowList list = this.newRowList(0);
        list.addRow(new Row(
                new TranslatableComponent("wskinloader.config.override.edit_title", this.editingPlayerName), null, new ArrayList<>()));

        list.addRow(booleanRow("wskinloader.config.override.skip_premium", null,
                override.skipPremiumCheck, v -> {
                    override.skipPremiumCheck = v;
                    this.markDirty();
                }));

        list.addRow(new Row(new TranslatableComponent("wskinloader.config.override.mapping_section"), null, new ArrayList<>()));

        list.addRow(mappingRow("wskinloader.config.override.mapping_skin",
                override.skinSourcePlayer,
                () -> { this.editingMappingIsSkin = true; this.settingMapping = true; this.refresh(); },
                () -> { override.skinSourcePlayer = ""; this.markDirty(); this.refresh(); }));
        list.addRow(mappingRow("wskinloader.config.override.mapping_cape",
                override.capeSourcePlayer,
                () -> { this.editingMappingIsSkin = false; this.settingMapping = true; this.refresh(); },
                () -> { override.capeSourcePlayer = ""; this.markDirty(); this.refresh(); }));

        list.addRow(new Row(new TranslatableComponent("wskinloader.config.override.model_type"), null,
                new ArrayList<>(java.util.Collections.singletonList(
                        modelButton(override)))));

        list.addRow(new Row(new TranslatableComponent("wskinloader.config.override.skin"), null, new ArrayList<>()));
        list.addRow(booleanRow("wskinloader.config.override.use_custom_api", null,
                override.skin.useCustomApi, v -> {
                    override.skin.useCustomApi = v;
                    this.markDirty();
                    this.refresh();
                }));
        if (override.skin.useCustomApi) {
            list.addRow(this.apiIndexRow(override.skin, this.editData.skinApis));
        }

        list.addRow(new Row(new TranslatableComponent("wskinloader.config.override.cape"), null, new ArrayList<>()));
        list.addRow(booleanRow("wskinloader.config.override.use_custom_api", null,
                override.cape.useCustomApi, v -> {
                    override.cape.useCustomApi = v;
                    this.markDirty();
                    this.refresh();
                }));
        if (override.cape.useCustomApi) {
            list.addRow(this.apiIndexRow(override.cape, this.editData.capeApis));
        }

        int doneY = this.contentBottom() - BUTTON_HEIGHT;
        this.addContentWidget(new Button(this.contentLeft(), doneY, 100, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.done"), b -> {
                    this.resetFormState();
                    this.refresh();
                }));
    }

    private Row apiIndexRow(ModConfig.TextureOverride texture, List<ApiConfig> apis) {
        String allLabel = new TranslatableComponent("wskinloader.config.override.all").getString();
        final String[] states = new String[apis.size() + 1];
        states[0] = allLabel;
        for (int i = 0; i < apis.size(); i++) {
            states[i + 1] = String.valueOf(i);
        }
        int clamped = texture.apiIndex >= -1 && texture.apiIndex < apis.size() ? texture.apiIndex : -1;
        String current = clamped == -1 ? allLabel : String.valueOf(clamped);

        Button button = new Button(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, new TextComponent(current), b -> {
            int idx = indexOf(states, b.getMessage().getString());
            String next = states[(idx + 1) % states.length];
            b.setMessage(new TextComponent(next));
            texture.apiIndex = next.equals(allLabel) ? -1 : Integer.parseInt(next);
            this.markDirty();
        });
        return new Row(new TranslatableComponent("wskinloader.config.override.api_index_short"), null,
                new ArrayList<>(java.util.Collections.singletonList(button)));
    }

    private Button modelButton(PlayerOverride override) {
        final String[] states = {"auto", "slim", "wide"};
        String current = "auto";
        for (String s : states) {
            if (s.equals(override.modelType)) {
                current = s;
                break;
            }
        }
        return new Button(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, modelLabel(current), b -> {
            String cur = b.getMessage().getString();
            int idx = -1;
            for (int i = 0; i < states.length; i++) {
                if (modelLabel(states[i]).getString().equals(cur)) {
                    idx = i;
                    break;
                }
            }
            String next = states[(idx + 1) % states.length];
            b.setMessage(modelLabel(next));
            override.modelType = next;
            this.markDirty();
        });
    }

    private static Component modelLabel(String value) {
        String key = "auto".equals(value) ? "wskinloader.config.override.model_auto"
                : "slim".equals(value) ? "wskinloader.config.override.model_slim"
                : "wskinloader.config.override.model_wide";
        return new TranslatableComponent(key);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private Row mappingRow(String labelKey, String source,
                                   Runnable setAction, Runnable clearAction) {
        boolean empty = source == null || source.trim().isEmpty();
        Component sub = empty
                ? new TranslatableComponent("wskinloader.config.override.mapping_self")
                : new TextComponent(source);
        Button set = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.set"), b -> setAction.run());
        Button clear = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.button.clear"), b -> clearAction.run());
        List<AbstractWidget> controls = new ArrayList<>();
        controls.add(set);
        controls.add(clear);
        return new Row(new TranslatableComponent(labelKey), sub, controls);
    }

    // -- Tab 4: Cache ---------------------------------------------------

    private void buildCacheTab() {
        List<UUID> cachedPlayers = new ArrayList<>(SkinCache.getCachedPlayers());

        RowList list = this.newRowList(0);
        Button clearAll = new Button(0, 0, 140, BUTTON_HEIGHT,
                new TranslatableComponent("wskinloader.config.cache.clear_all"), b -> {
                    SkinLoader.invalidateAll();
                    this.refresh();
                });
        list.addRow(new Row(new TranslatableComponent("wskinloader.config.cache.stats"), null,
                new ArrayList<>(java.util.Collections.singletonList(clearAll))));

        list.addRow(new Row(new TranslatableComponent("wskinloader.config.cache.count", cachedPlayers.size()),
                null, new ArrayList<>()));

        if (cachedPlayers.isEmpty()) {
            list.addRow(new Row(new TranslatableComponent("wskinloader.config.cache.empty"), null, new ArrayList<>()));
            return;
        }

        for (UUID uuid : cachedPlayers) {
            String resolvedName = PlayerNameCache.getName(uuid);
            if (resolvedName == null) {
                resolvedName = uuid.toString().substring(0, 8) + "...";
            }
            String resolvedSource = PlayerSkinSourceCache.getSource(uuid);
            if (resolvedSource == null) {
                resolvedSource = "unknown";
            }
            final String displayName = resolvedName;
            final UUID playerId = uuid;

            Button preview = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.preview"), b -> {
                        if (this.minecraft != null) {
                            this.minecraft.setScreen(new SkinPreviewScreen(this, displayName, playerId,
                                    SkinCache.getSkin(playerId)));
                        }
                    });
            Button delete = new Button(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                    new TranslatableComponent("wskinloader.config.button.delete"), b -> {
                        SkinLoader.reload(playerId);
                        this.refresh();
                    });
            List<AbstractWidget> controls = new ArrayList<>();
            controls.add(preview);
            controls.add(delete);
            list.addRow(new Row(new TextComponent(displayName),
                    new TextComponent("[" + resolvedSource + "] " + uuid.toString().substring(0, 13) + "..."),
                    controls));
        }
    }

    private Row booleanRow(String labelKey, String descKey, boolean initial,
                                   java.util.function.Consumer<Boolean> onChange) {
        final boolean[] value = {initial};
        Button button = new Button(0, 0, CONTROL_WIDTH, BUTTON_HEIGHT, onOff(value[0]), b -> {
            value[0] = !value[0];
            b.setMessage(onOff(value[0]));
            onChange.accept(value[0]);
        });
        return new Row(new TranslatableComponent(labelKey), descKey == null ? null : new TranslatableComponent(descKey),
                new ArrayList<>(java.util.Collections.singletonList(button)));
    }

    private static Component onOff(boolean value) {
        return new TranslatableComponent(value
                ? "wskinloader.config.override.yes" : "wskinloader.config.override.no");
    }

    private void addLabel(Component text, int x, int y) {
        this.addContentWidget(new net.minecraft.client.gui.components.AbstractWidget(x, y, 200, 10, text) {
            @Override public void render(PoseStack stack, int mouseX, int mouseY, float delta) {
                drawString(stack, ConfigScreen.this.font, this.getMessage().getString(), this.x, this.y, 0xFFE0E0E6);
            }
        });
    }

    // ------------------------------------------------------------------
    // RENDERING
    // ------------------------------------------------------------------

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float delta) {
        int top = 22;
        int bottom = this.height - 30;
        drawPanel(stack, this.sidebarLeft() - 4, top, SIDEBAR_WIDTH + 8, bottom - top);
        drawPanel(stack, this.contentLeft() - 6, top, this.contentWidth() + 12, bottom - top);

        // 1.16.5's Screen.render only renders the button list, so lists added
        // via addWidget must be rendered explicitly.
        if (this.pageList != null) {
            this.pageList.render(stack, mouseX, mouseY, delta);
        }
        for (AbstractSelectionList<?> list : this.rowLists) {
            list.render(stack, mouseX, mouseY, delta);
        }

        super.render(stack, mouseX, mouseY, delta);

        drawCenteredString(stack, this.font, this.title.getString(), this.width / 2, 10, 0xFFFFFFFF);
        if (this.validationError != null) {
            drawCenteredString(stack, this.font, this.validationError, this.width / 2, 20, 0xFFFF5555);
        }
    }

    private void drawPanel(PoseStack stack, int x, int y, int w, int h) {
        fill(stack, x, y, x + w, y + h, 0x66000000);
        fill(stack, x, y, x + w, y + 1, 0x33FFFFFF);
        fill(stack, x, y + h - 1, x + w, y + h, 0x33FFFFFF);
        fill(stack, x, y, x + 1, y + h, 0x33FFFFFF);
        fill(stack, x + w - 1, y, x + w, y + h, 0x33FFFFFF);
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
    public void onClose() {
        this.requestClose();
    }

    private void requestClose() {
        if (this.dirty && this.minecraft != null) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) {
                            this.goToParent();
                        } else if (this.minecraft != null) {
                            this.minecraft.setScreen(this);
                        }
                    },
                    new TranslatableComponent("wskinloader.config.title"),
                    new TranslatableComponent("wskinloader.config.confirm_discard"),
                    new TranslatableComponent("wskinloader.config.confirm_discard.yes"),
                    new TranslatableComponent("wskinloader.config.confirm_discard.no")));
        } else {
            this.goToParent();
        }
    }

    private void goToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
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
    // LEFT CATEGORY LIST
    // ------------------------------------------------------------------

    private class PageList extends ObjectSelectionList<PageList.PageEntry> {

        PageList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, y + height, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
            for (int i = 0; i < TAB_KEYS.length; i++) {
                this.addEntry(new PageEntry(i, new TranslatableComponent(TAB_KEYS[i])));
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

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            PageEntry entry = this.getEntryAtPosition(mouseX, mouseY);
            if (entry != null) {
                this.setSelected(entry);
                ConfigScreen.this.selectTab(entry.index);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        class PageEntry extends ObjectSelectionList.Entry<PageEntry> {
            private final int index;
            private final Component label;

            PageEntry(int index, Component label) {
                this.index = index;
                this.label = label;
            }

            @Override
            public void render(PoseStack stack, int entryIndex, int rowTop, int rowLeft, int rowWidth,
                               int rowHeight, int mouseX, int mouseY, boolean hovered, float partial) {
                boolean selected = PageList.this.getSelected() == this;
                int color = selected ? 0xFFFFFFFF : (hovered ? 0xFFE8E8F0 : 0xFFB0B0BC);
                int y = rowTop + (rowHeight - 9) / 2;
                drawString(stack, ConfigScreen.this.font,
                        trim(ConfigScreen.this.font, this.label.getString(), rowWidth - 10), rowLeft + 6, y, color);
                if (selected) {
                    fill(stack, rowLeft, rowTop + 2, rowLeft + 2, rowTop + rowHeight - 2, 0xFF2BB673);
                }
            }
        }
    }

    private static String trim(net.minecraft.client.gui.Font font, String text, int maxWidth) {
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

    private static class Row {
        final Component label;
        final Component sub;
        final List<AbstractWidget> controls;

        Row(Component label, Component sub, List<AbstractWidget> controls) {
            this.label = label;
            this.sub = sub;
            this.controls = controls;
        }
    }

    private class RowList extends AbstractSelectionList<RowList.RowEntry> {

        RowList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, y + height, itemHeight);
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);
        }

        void addRow(Row row) {
            this.addEntry(new RowEntry(row, this.children().size()));
        }

        @Override
        public int getRowWidth() {
            return this.width;
        }

        class RowEntry extends AbstractSelectionList.Entry<RowEntry> {
            private final Row row;
            private final int index;

            RowEntry(Row row, int index) {
                this.row = row;
                this.index = index;
            }

            @Override
            public void render(PoseStack stack, int entryIndex, int rowTop, int rowLeft, int rowWidth,
                               int rowHeight, int mouseX, int mouseY, boolean hovered, float partial) {
                if (hovered) {
                    fill(stack, rowLeft, rowTop - 1, rowLeft + rowWidth, rowTop + rowHeight - 1, 0x33FFFFFF);
                }
                int textY = rowTop + (this.row.sub == null ? (rowHeight - 8) / 2 : 2);
                drawString(stack, ConfigScreen.this.font, this.row.label.getString(), rowLeft + 2, textY, 0xFFE0E0E6);
                if (this.row.sub != null) {
                    drawString(stack, ConfigScreen.this.font, this.row.sub.getString(), rowLeft + 2, textY + 11, 0xFF9A9AA6);
                }

                int controlRight = rowLeft + rowWidth - 2;
                for (int i = this.row.controls.size() - 1; i >= 0; i--) {
                    AbstractWidget control = this.row.controls.get(i);
                    control.x = controlRight - control.getWidth();
                    control.y = rowTop + (rowHeight - control.getHeight()) / 2;
                    control.render(stack, mouseX, mouseY, partial);
                    controlRight = control.x - GAP;
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                for (AbstractWidget control : this.row.controls) {
                    if (control.isMouseOver(mouseX, mouseY) && control.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
