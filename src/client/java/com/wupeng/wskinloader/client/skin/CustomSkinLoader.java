package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;

import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 从用户配置的自定义 API 下载皮肤 / 披风。
 *
 * <p>Returns URL descriptors only; SkinLoader validates each download before
 * recording the texture and its source in the player cache.
 */
public class CustomSkinLoader {

    public static CompletableFuture<MinecraftProfileTexture> loadCustomSkin(
        UUID playerUuid,
        String playerName,
        MinecraftProfileTexture.Type type
    ) {
        return loadCustomSkinFromIndex(playerUuid, playerName, type, 0);
    }

    public static CompletableFuture<MinecraftProfileTexture> loadCustomSkinFromIndex(
        UUID playerUuid,
        String playerName,
        MinecraftProfileTexture.Type type,
        int apiIndex
    ) {
        ModConfig config = ModConfig.getInstance();
        List<ModConfig.ApiConfig> apis = type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis;

        if (apis.isEmpty() || apiIndex < 0 || apiIndex >= apis.size()) {
            return completed(null);
        }

        ModConfig.ApiConfig api = apis.get(apiIndex);
        String url = buildUrl(api.url, playerName);
        if (url == null) return completed(null);

        java.util.Map<String, String> metadata = new java.util.HashMap<>();
        if (api.alias != null) {
            metadata.put("wskinloader_source", api.alias);
        }
        return completed(new MinecraftProfileTexture(url, metadata));
    }

    private static String buildUrl(String template, String playerName) {
        if (!ModConfig.isAllowedApiUrl(template)) return null;
        try {
            return template.replace("%name%", URLEncoder.encode(playerName, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
    }

    private static CompletableFuture<MinecraftProfileTexture> completed(MinecraftProfileTexture texture) {
        return CompletableFuture.completedFuture(texture);
    }
}
