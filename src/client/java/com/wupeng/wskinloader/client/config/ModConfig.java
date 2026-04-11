package com.wupeng.wskinloader.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("wskinloader.json");
    
    private static ModConfig INSTANCE;
    
    public List<String> skinUrls = new ArrayList<>();
    public List<String> capeUrls = new ArrayList<>();
    
    // Tab 列表显示头像开关
    public boolean enableTabListHeads = true;
    
    // 玩家覆盖配置：玩家名 -> 覆盖规则
    public Map<String, PlayerOverride> playerOverrides = new HashMap<>();
    
    public static class PlayerOverride {
        // 是否跳过正版检测
        public boolean skipPremiumCheck = false;
        
        // 皮肤配置
        public TextureOverride skin = new TextureOverride();
        
        // 披风配置
        public TextureOverride cape = new TextureOverride();
        
        public PlayerOverride() {}
    }
    
    public static class TextureOverride {
        // 是否使用自定义 API（false = 使用正版）
        public boolean useCustomApi = true;
        
        // 使用哪个 API（索引，-1 表示使用所有 API 按顺序尝试）
        public int apiIndex = -1;
        
        public TextureOverride() {}
    }
    
    public ModConfig() {
        skinUrls.add("https://littleskin.cn/skin/%name%.png");
        capeUrls.add("https://littleskin.cn/cape/%name%.png");
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
                return GSON.fromJson(json, ModConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }
    
    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 获取玩家的覆盖配置
     */
    public PlayerOverride getPlayerOverride(String playerName) {
        return playerOverrides.get(playerName);
    }
    
    /**
     * 添加或更新玩家覆盖配置
     */
    public void setPlayerOverride(String playerName, PlayerOverride override) {
        playerOverrides.put(playerName, override);
        save();
    }
    
    /**
     * 移除玩家覆盖配置
     */
    public void removePlayerOverride(String playerName) {
        playerOverrides.remove(playerName);
        save();
    }
}
