package com.wupeng.wskinloader.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Map;

public class ModMenuIntegration implements ModMenuApi {
    
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return this::createConfigScreen;
    }
    
    private Screen createConfigScreen(Screen parent) {
        ModConfig config = ModConfig.getInstance();
        
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("WSkinLoader 配置"));
        
        builder.setSavingRunnable(config::save);
        
        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        
        // 通用设置
        ConfigCategory generalCategory = builder.getOrCreateCategory(Component.literal("通用设置"));
        generalCategory.addEntry(entryBuilder.startBooleanToggle(
            Component.literal("Tab 列表显示头像"),
            config.enableTabListHeads
        )
        .setDefaultValue(true)
        .setTooltip(Component.literal("在多人游戏的 Tab 列表中显示玩家头像"))
        .setSaveConsumer(newValue -> config.enableTabListHeads = newValue)
        .build());
        
        // 皮肤 URL 配置
        ConfigCategory skinCategory = builder.getOrCreateCategory(Component.literal("皮肤 API"));
        skinCategory.addEntry(entryBuilder.startStrList(
            Component.literal("皮肤 URL 列表"),
            config.skinUrls
        )
        .setDefaultValue(new ArrayList<>() {{
            add("https://littleskin.cn/skin/%name%.png");
        }})
        .setTooltip(Component.literal("使用 %name% 作为玩家名占位符"))
        .setSaveConsumer(newList -> config.skinUrls = new ArrayList<>(newList))
        .build());
        
        // 披风 URL 配置
        ConfigCategory capeCategory = builder.getOrCreateCategory(Component.literal("披风 API"));
        capeCategory.addEntry(entryBuilder.startStrList(
            Component.literal("披风 URL 列表"),
            config.capeUrls
        )
        .setDefaultValue(new ArrayList<>() {{
            add("https://littleskin.cn/cape/%name%.png");
        }})
        .setTooltip(Component.literal("使用 %name% 作为玩家名占位符"))
        .setSaveConsumer(newList -> config.capeUrls = new ArrayList<>(newList))
        .build());
        
        // 玩家覆盖配置
        ConfigCategory overrideCategory = builder.getOrCreateCategory(Component.literal("玩家覆盖"));
        overrideCategory.addEntry(entryBuilder.startTextDescription(
            Component.literal("§7为特定玩家配置自定义规则\n§7格式: 玩家名=配置JSON\n§7留空表示删除配置")
        ).build());
        
        ArrayList<String> overrideList = new ArrayList<>();
        for (Map.Entry<String, ModConfig.PlayerOverride> entry : config.playerOverrides.entrySet()) {
            overrideList.add(entry.getKey());
        }
        
        overrideCategory.addEntry(entryBuilder.startStrList(
            Component.literal("玩家列表"),
            overrideList
        )
        .setTooltip(
            Component.literal("添加玩家名以配置覆盖规则\n"),
            Component.literal("配置文件中手动编辑详细规则")
        )
        .setSaveConsumer(newList -> {
            for (String playerName : newList) {
                if (!config.playerOverrides.containsKey(playerName)) {
                    ModConfig.PlayerOverride override = new ModConfig.PlayerOverride();
                    override.skipPremiumCheck = true;
                    config.playerOverrides.put(playerName, override);
                }
            }
            config.playerOverrides.keySet().retainAll(newList);
        })
        .build());
        
        overrideCategory.addEntry(entryBuilder.startTextDescription(
            Component.literal("§e高级配置请编辑配置文件：\n§7.minecraft/config/wskinloader.json")
        ).build());
        
        return builder.build();
    }
}
