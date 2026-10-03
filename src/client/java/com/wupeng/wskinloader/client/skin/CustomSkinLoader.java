package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 从用户配置的自定义 API 下载皮肤 / 披风。
 *
 * <p>1.16.5 使用 {@link MinecraftProfileTexture} 描述一个待注册的纹理：
 * 它的 {@code hash} 字段决定缓存文件名与纹理 ID，{@code getUrl()} 是下载地址。
 */
public class CustomSkinLoader {

    public static CompletableFuture<MinecraftProfileTexture> loadCustomSkin(
        UUID playerUuid,
        String playerName,
        MinecraftProfileTexture.Type type
    ) {
        return tryLoadFromApis(playerUuid, playerName, type, 0);
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

        MinecraftProfileTexture texture = new MinecraftProfileTexture(url, null);
        if (type == MinecraftProfileTexture.Type.SKIN) {
            PlayerSkinSourceCache.setSource(playerUuid, api.alias);
        }
        return completed(texture);
    }

    private static CompletableFuture<MinecraftProfileTexture> tryLoadFromApis(
        UUID playerUuid,
        String playerName,
        MinecraftProfileTexture.Type type,
        int index
    ) {
        ModConfig config = ModConfig.getInstance();
        List<ModConfig.ApiConfig> apis = type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis;

        if (index >= apis.size()) {
            return completed(null);
        }

        ModConfig.ApiConfig api = apis.get(index);
        String url = buildUrl(api.url, playerName);
        if (url == null) return completed(null);

        MinecraftProfileTexture texture = new MinecraftProfileTexture(url, null);
        if (type == MinecraftProfileTexture.Type.SKIN) {
            PlayerSkinSourceCache.setSource(playerUuid, api.alias);
        }
        return completed(texture);
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
