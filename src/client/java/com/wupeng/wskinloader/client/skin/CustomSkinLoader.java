package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CustomSkinLoader {
    
    public static CompletableFuture<ClientAsset.Texture> loadCustomSkin(
        UUID playerUuid,
        String playerName,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type
    ) {
        ModConfig config = ModConfig.getInstance();
        List<ModConfig.ApiConfig> apis = type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis;
        
        if (apis.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return tryLoadFromApis(playerUuid, playerName, apis, 0, downloader, cachePath, textureId, type);
    }
    
    public static CompletableFuture<ClientAsset.Texture> loadCustomSkinFromIndex(
        UUID playerUuid,
        String playerName,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type,
        int apiIndex
    ) {
        ModConfig config = ModConfig.getInstance();
        List<ModConfig.ApiConfig> apis = type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis;
        
        if (apis.isEmpty() || apiIndex < 0 || apiIndex >= apis.size()) {
            return CompletableFuture.completedFuture(null);
        }
        
        ModConfig.ApiConfig api = apis.get(apiIndex);
        String url = api.url.replace("%name%", playerName);
        
        return downloader.downloadAndRegisterSkin(textureId, cachePath, url, type == MinecraftProfileTexture.Type.SKIN)
            .thenApply(result -> {
                if (result != null && type == MinecraftProfileTexture.Type.SKIN) {
                    // 记录皮肤来源
                    PlayerSkinSourceCache.setSource(playerUuid, api.alias);
                }
                return result;
            })
            .exceptionally(throwable -> null);
    }
    
    private static CompletableFuture<ClientAsset.Texture> tryLoadFromApis(
        UUID playerUuid,
        String playerName,
        List<ModConfig.ApiConfig> apis,
        int index,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type
    ) {
        if (index >= apis.size()) {
            return CompletableFuture.completedFuture(null);
        }
        
        ModConfig.ApiConfig api = apis.get(index);
        String url = api.url.replace("%name%", playerName);
        
        return downloader.downloadAndRegisterSkin(textureId, cachePath, url, type == MinecraftProfileTexture.Type.SKIN)
            .exceptionally(throwable -> null)
            .thenCompose(result -> {
                if (result != null) {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        // 记录皮肤来源
                        PlayerSkinSourceCache.setSource(playerUuid, api.alias);
                    }
                    return CompletableFuture.completedFuture(result);
                }
                return tryLoadFromApis(playerUuid, playerName, apis, index + 1, downloader, cachePath, textureId, type);
            });
    }
}
