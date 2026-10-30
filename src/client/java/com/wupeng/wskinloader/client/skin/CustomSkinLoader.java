package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class CustomSkinLoader {
    private static final System.Logger LOGGER = System.getLogger("wskinloader");

    public record LoadedTexture(ClientAsset.Texture asset, String sourceAlias, Path cachePath,
                                net.minecraft.world.entity.player.PlayerModelType model) {}

    private CustomSkinLoader() {}

    public static CompletableFuture<LoadedTexture> load(List<ModConfig.ApiConfig> apis, String playerName,
            SkinTextureDownloader downloader, Path cachePath, Identifier textureId,
            MinecraftProfileTexture.Type type, int apiIndex, boolean refresh, BooleanSupplier active) {
        if (playerName == null || playerName.isBlank() || apis == null || apis.isEmpty())
            return CompletableFuture.completedFuture(null);
        if (apiIndex >= apis.size() || apiIndex < -1) return CompletableFuture.completedFuture(null);
        int start = apiIndex < 0 ? 0 : apiIndex;
        return tryLoad(apis, start, apiIndex < 0 ? apis.size() : start + 1, playerName, downloader,
                cachePath, textureId, type, refresh, active, null);
    }

    private static CompletableFuture<LoadedTexture> tryLoad(List<ModConfig.ApiConfig> apis, int index, int end,
            String playerName, SkinTextureDownloader downloader, Path basePath, Identifier textureId,
            MinecraftProfileTexture.Type type, boolean refresh, BooleanSupplier active, Throwable lastError) {
        if (!active.getAsBoolean()) return CompletableFuture.completedFuture(null);
        if (index >= end) return lastError == null ? CompletableFuture.completedFuture(null)
                : CompletableFuture.failedFuture(lastError);
        ModConfig.ApiConfig api = apis.get(index);
        String url = buildUrl(api == null ? null : api.url, playerName);
        if (url == null) {
            LOGGER.log(System.Logger.Level.DEBUG, "Skipping invalid texture API at index {0}", index);
            return tryLoad(apis, index + 1, end, playerName, downloader, basePath, textureId,
                    type, refresh, active, lastError);
        }
        Path sourcePath = sourceCachePath(basePath, url);
        return SafeTextureDownloader.downloadDetailed(downloader, textureId, sourcePath, url,
                        type == MinecraftProfileTexture.Type.SKIN, refresh, active)
                .handle((downloaded, error) -> {
                    if (downloaded != null) {
                        if (active.getAsBoolean()) return CompletableFuture.completedFuture(new LoadedTexture(downloaded.asset(),
                                api.alias == null ? "" : api.alias, sourcePath, downloaded.model()));
                        SafeTextureDownloader.discard(downloaded.asset());
                        return CompletableFuture.<LoadedTexture>completedFuture(null);
                    }
                    if (error != null) LOGGER.log(System.Logger.Level.DEBUG, "Texture API failed at index " + index, error);
                    return tryLoad(apis, index + 1, end, playerName, downloader, basePath, textureId,
                            type, refresh, active, error == null ? lastError : error);
                }).thenCompose(future -> future);
    }

    static String buildUrl(String template, String playerName) {
        if (template == null || playerName == null || playerName.isBlank() || !ModConfig.isAllowedApiUrl(template)) return null;
        String url = template.replace("%name%", java.net.URLEncoder.encode(playerName, StandardCharsets.UTF_8));
        return ModConfig.isAllowedApiUrl(url) ? url : null;
    }

    static Path sourceCachePath(Path basePath, String url) {
        String key = digest(url);
        Path root = basePath.getParent();
        if (root != null) root = root.getParent();
        if (root == null) root = basePath.getParent() == null ? basePath : basePath.getParent();
        return root.resolve(key.substring(0, 2)).resolve(key);
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte b : digest) result.append(Character.forDigit((b >>> 4) & 15, 16)).append(Character.forDigit(b & 15, 16));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
