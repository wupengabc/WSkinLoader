package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CustomSkinLoader {
    
    public static CompletableFuture<ClientAsset.Texture> loadCustomSkin(
        String playerName,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type
    ) {
        ModConfig config = ModConfig.getInstance();
        List<String> urls = type == MinecraftProfileTexture.Type.SKIN ? config.skinUrls : config.capeUrls;
        
        if (urls.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        
        return tryLoadFromUrls(playerName, urls, 0, downloader, cachePath, textureId, type);
    }
    
    public static CompletableFuture<ClientAsset.Texture> loadCustomSkinFromIndex(
        String playerName,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type,
        int apiIndex
    ) {
        ModConfig config = ModConfig.getInstance();
        List<String> urls = type == MinecraftProfileTexture.Type.SKIN ? config.skinUrls : config.capeUrls;
        
        if (urls.isEmpty() || apiIndex < 0 || apiIndex >= urls.size()) {
            return CompletableFuture.completedFuture(null);
        }
        
        String url = urls.get(apiIndex).replace("%name%", playerName);
        
        return downloader.downloadAndRegisterSkin(textureId, cachePath, url, type == MinecraftProfileTexture.Type.SKIN)
            .exceptionally(throwable -> null);
    }
    
    private static CompletableFuture<ClientAsset.Texture> tryLoadFromUrls(
        String playerName,
        List<String> urls,
        int index,
        SkinTextureDownloader downloader,
        Path cachePath,
        Identifier textureId,
        MinecraftProfileTexture.Type type
    ) {
        if (index >= urls.size()) {
            return CompletableFuture.completedFuture(null);
        }
        
        String url = urls.get(index).replace("%name%", playerName);
        
        return downloader.downloadAndRegisterSkin(textureId, cachePath, url, type == MinecraftProfileTexture.Type.SKIN)
            .exceptionally(throwable -> null)
            .thenCompose(result -> {
                if (result != null) {
                    return CompletableFuture.completedFuture(result);
                }
                return tryLoadFromUrls(playerName, urls, index + 1, downloader, cachePath, textureId, type);
            });
    }
}
