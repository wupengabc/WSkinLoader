package com.wupeng.wskinloader.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("wskinloader");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("wskinloader.json");

    private static ModConfig INSTANCE;

    /** Guards against concurrent saves; written from the render thread, read from the async thread. */
    private volatile boolean isSaving = false;

    // 皮肤API配置列表（URL和别名）
    public List<ApiConfig> skinApis = new ArrayList<>();
    
    // 披风API配置列表（URL和别名）
    public List<ApiConfig> capeApis = new ArrayList<>();
    
    // 向后兼容的URL列表（已废弃，仅用于迁移）
    @Deprecated
    public List<String> skinUrls = new ArrayList<>();
    @Deprecated
    public List<String> capeUrls = new ArrayList<>();

    // Tab 列表显示头像开关
    // 描述：在Tab玩家列表中显示玩家头像
    public boolean enableTabListHeads = true;
    
    // 玩家名称旁显示皮肤来源标签
    // 描述：在游戏世界中玩家名称标签右侧显示皮肤API来源（如"LittleSkin"、"正版"等）
    public boolean enableNameTagLabel = true;

    // 玩家覆盖配置：玩家名 -> 覆盖规则
    public Map<String, PlayerOverride> playerOverrides = new HashMap<>();

    // -------------------------------------------------------------------------
    // 内部数据类
    // -------------------------------------------------------------------------

    /**
     * API配置：包含URL和显示别名
     */
    public static class ApiConfig {
        // API的URL地址，支持 %name% 占位符
        public String url;
        
        // API的显示别名（如"LittleSkin"、"Blessing Skin"等）
        public String alias;
        
        public ApiConfig() {}
        
        public ApiConfig(String url, String alias) {
            this.url = url;
            this.alias = alias;
        }
        
        public ApiConfig deepCopy() {
            return new ApiConfig(this.url, this.alias);
        }
    }

    public static class PlayerOverride {
        // 是否跳过正版检测
        // 描述：启用后将不检查该玩家是否为正版，直接使用自定义API
        public boolean skipPremiumCheck = false;

        // 模型类型配置
        // 描述："auto" = 自动检测, "slim" = 瘦模型, "wide" = 宽模型
        public String modelType = "auto";

        // 皮肤配置
        // 描述：该玩家的皮肤加载规则
        public TextureOverride skin = new TextureOverride();

        // 披风配置
        // 描述：该玩家的披风加载规则
        public TextureOverride cape = new TextureOverride();

        public PlayerOverride() {}

        /** 深拷贝 */
        public PlayerOverride deepCopy() {
            PlayerOverride copy = new PlayerOverride();
            copy.skipPremiumCheck = this.skipPremiumCheck;
            copy.modelType = this.modelType;
            copy.skin = this.skin.deepCopy();
            copy.cape = this.cape.deepCopy();
            return copy;
        }

        /** 验证字段合法性 */
        public boolean isValid(int skinUrlCount, int capeUrlCount) {
            return skin.isValid(skinUrlCount) && cape.isValid(capeUrlCount);
        }
    }

    public static class TextureOverride {
        // 是否使用自定义 API（false = 使用正版）
        // 描述：关闭后将使用Mojang正版皮肤/披风
        public boolean useCustomApi = true;

        // 使用哪个 API（索引，-1 表示使用所有 API 按顺序尝试）
        // 描述：指定使用第几个API（从0开始），-1表示按顺序尝试所有API直到成功
        public int apiIndex = -1;

        public TextureOverride() {}

        /** 深拷贝 */
        public TextureOverride deepCopy() {
            TextureOverride copy = new TextureOverride();
            copy.useCustomApi = this.useCustomApi;
            copy.apiIndex = this.apiIndex;
            return copy;
        }

        /**
         * 验证 apiIndex 合法性。
         * apiIndex == -1 表示"全部尝试"，始终合法；
         * 否则必须在 [0, urlCount) 范围内。
         */
        public boolean isValid(int urlCount) {
            return apiIndex == -1 || (apiIndex >= 0 && apiIndex < urlCount);
        }
    }

    // -------------------------------------------------------------------------
    // ConfigData —— 配置字段的纯数据载体，用于界面编辑时的深拷贝副本
    // -------------------------------------------------------------------------

    public static class ConfigData {
        public boolean enableTabListHeads = true;
        public boolean enableNameTagLabel = true;
        public List<ApiConfig> skinApis = new ArrayList<>();
        public List<ApiConfig> capeApis = new ArrayList<>();
        public Map<String, PlayerOverride> playerOverrides = new HashMap<>();

        public ConfigData() {}

        /** 深拷贝自身 */
        public ConfigData deepCopy() {
            ConfigData copy = new ConfigData();
            copy.enableTabListHeads = this.enableTabListHeads;
            copy.enableNameTagLabel = this.enableNameTagLabel;
            copy.skinApis = new ArrayList<>();
            for (ApiConfig api : this.skinApis) {
                copy.skinApis.add(api.deepCopy());
            }
            copy.capeApis = new ArrayList<>();
            for (ApiConfig api : this.capeApis) {
                copy.capeApis.add(api.deepCopy());
            }
            copy.playerOverrides = new HashMap<>();
            for (Map.Entry<String, PlayerOverride> entry : this.playerOverrides.entrySet()) {
                copy.playerOverrides.put(entry.getKey(), entry.getValue().deepCopy());
            }
            return copy;
        }

        /**
         * 验证整个配置数据的合法性。
         * @return null 表示合法；否则返回第一条错误描述。
         */
        public String validate() {
            for (Map.Entry<String, PlayerOverride> entry : playerOverrides.entrySet()) {
                String name = entry.getKey();
                if (name == null || name.isBlank()) {
                    return "玩家名不能为空";
                }
                PlayerOverride override = entry.getValue();
                if (!override.isValid(skinApis.size(), capeApis.size())) {
                    return "玩家 \"" + name + "\" 的 API 索引超出范围";
                }
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 构造 / 单例
    // -------------------------------------------------------------------------

    public ModConfig() {
        // 默认添加LittleSkin API
        skinApis.add(new ApiConfig("https://littleskin.cn/skin/%name%.png", "LittleSkin"));
        capeApis.add(new ApiConfig("https://littleskin.cn/cape/%name%.png", "LittleSkin"));
        
        // 确保默认启用名称标签显示（修复首次安装时不显示的bug）
        this.enableNameTagLabel = true;
        this.enableTabListHeads = true;
    }

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH);
                ModConfig config = GSON.fromJson(json, ModConfig.class);
                if (config != null) {
                    // 向后兼容：确保集合字段不为 null
                    if (config.skinApis == null) config.skinApis = new ArrayList<>();
                    if (config.capeApis == null) config.capeApis = new ArrayList<>();
                    if (config.playerOverrides == null) config.playerOverrides = new HashMap<>();
                    
                    // 迁移旧配置：将skinUrls/capeUrls转换为skinApis/capeApis
                    if (config.skinUrls != null && !config.skinUrls.isEmpty() && config.skinApis.isEmpty()) {
                        for (String url : config.skinUrls) {
                            String alias = guessAliasFromUrl(url);
                            config.skinApis.add(new ApiConfig(url, alias));
                        }
                        config.skinUrls.clear();
                    }
                    if (config.capeUrls != null && !config.capeUrls.isEmpty() && config.capeApis.isEmpty()) {
                        for (String url : config.capeUrls) {
                            String alias = guessAliasFromUrl(url);
                            config.capeApis.add(new ApiConfig(url, alias));
                        }
                        config.capeUrls.clear();
                    }
                    
                    // 修复：如果是从旧版本升级，确保enableNameTagLabel默认为true
                    // 这样用户不需要手动打开就能看到来源标签
                    // （GSON反序列化时，如果JSON中没有这个字段，会使用Java字段的默认值false）
                    // 我们通过检查配置文件内容来判断是否是旧版本
                    if (!json.contains("enableNameTagLabel")) {
                        config.enableNameTagLabel = true;
                        config.save(); // 保存更新后的配置
                    }
                    
                    return config;
                }
            } catch (IOException e) {
                LOGGER.error("[WSkinLoader] 读取配置文件失败，将使用默认配置", e);
            } catch (Exception e) {
                LOGGER.error("[WSkinLoader] 解析配置文件失败，将使用默认配置", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        // 序列化在当前线程进行，保证数据一致性
        final String json = GSON.toJson(this);
        
        // 同步写入文件，确保配置立即持久化
        // 避免异步写入时游戏退出或后续保存被跳过导致的数据丢失
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, json);
            LOGGER.info("[WSkinLoader] 配置已保存到 {}", CONFIG_PATH);
        } catch (IOException e) {
            LOGGER.error("[WSkinLoader] 保存配置文件失败: {}", CONFIG_PATH, e);
        }
    }
    
    /**
     * 异步保存配置（用于频繁调用的场景，如运行时自动保存）
     */
    public void saveAsync() {
        if (isSaving) return;
        isSaving = true;
        final String json = GSON.toJson(this);
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                Files.writeString(CONFIG_PATH, json);
                LOGGER.info("[WSkinLoader] 配置已异步保存到 {}", CONFIG_PATH);
            } catch (IOException e) {
                LOGGER.error("[WSkinLoader] 保存配置文件失败: {}", CONFIG_PATH, e);
            } finally {
                isSaving = false;
            }
        });
    }

    // -------------------------------------------------------------------------
    // 深拷贝
    // -------------------------------------------------------------------------

    /**
     * 返回当前配置的深拷贝（ConfigData），供界面编辑使用，不影响原始实例。
     */
    public ConfigData deepCopy() {
        ConfigData copy = new ConfigData();
        copy.enableTabListHeads = this.enableTabListHeads;
        copy.enableNameTagLabel = this.enableNameTagLabel;
        copy.skinApis = new ArrayList<>();
        for (ApiConfig api : this.skinApis) {
            copy.skinApis.add(api.deepCopy());
        }
        copy.capeApis = new ArrayList<>();
        for (ApiConfig api : this.capeApis) {
            copy.capeApis.add(api.deepCopy());
        }
        copy.playerOverrides = new HashMap<>();
        for (Map.Entry<String, PlayerOverride> entry : this.playerOverrides.entrySet()) {
            copy.playerOverrides.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return copy;
    }

    /**
     * 将 ConfigData 的所有字段写回本实例（保存前调用）。
     */
    public void applyFrom(ConfigData data) {
        this.enableTabListHeads = data.enableTabListHeads;
        this.enableNameTagLabel = data.enableNameTagLabel;
        this.skinApis = new ArrayList<>();
        for (ApiConfig api : data.skinApis) {
            this.skinApis.add(api.deepCopy());
        }
        this.capeApis = new ArrayList<>();
        for (ApiConfig api : data.capeApis) {
            this.capeApis.add(api.deepCopy());
        }
        this.playerOverrides = new HashMap<>();
        for (Map.Entry<String, PlayerOverride> entry : data.playerOverrides.entrySet()) {
            this.playerOverrides.put(entry.getKey(), entry.getValue().deepCopy());
        }
    }

    // -------------------------------------------------------------------------
    // 便捷方法
    // -------------------------------------------------------------------------

    /** 获取玩家的覆盖配置 */
    public PlayerOverride getPlayerOverride(String playerName) {
        return playerOverrides.get(playerName);
    }

    /** 添加或更新玩家覆盖配置 */
    public void setPlayerOverride(String playerName, PlayerOverride override) {
        playerOverrides.put(playerName, override);
        save();
    }

    /** 移除玩家覆盖配置 */
    public void removePlayerOverride(String playerName) {
        playerOverrides.remove(playerName);
        save();
    }
    
    // -------------------------------------------------------------------------
    // 辅助方法
    // -------------------------------------------------------------------------
    
    /**
     * 从URL猜测别名
     */
    private static String guessAliasFromUrl(String url) {
        if (url == null || url.isEmpty()) return "暂无别名";
        if (url.contains("littleskin.cn")) return "LittleSkin";
        if (url.contains("blessing.skin")) return "Blessing Skin";
        if (url.contains("mojang.com") || url.contains("minecraft.net")) return "正版";
        return "暂无别名";
    }
    
    /**
     * 获取皮肤API的URL列表（向后兼容）
     */
    public List<String> getSkinUrls() {
        List<String> urls = new ArrayList<>();
        for (ApiConfig api : skinApis) {
            urls.add(api.url);
        }
        return urls;
    }
    
    /**
     * 获取披风API的URL列表（向后兼容）
     */
    public List<String> getCapeUrls() {
        List<String> urls = new ArrayList<>();
        for (ApiConfig api : capeApis) {
            urls.add(api.url);
        }
        return urls;
    }
}
