package com.wupeng.wskinloader.client.skin;

import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一的皮肤加载工具类
 */
public class SkinLoader {
    
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    
    public static void loadSkinForProfile(GameProfile profile, SkinTextureDownloader downloader) {
        if (profile == null || profile.id() == null || profile.name() == null) {
            return;
        }
        
        UUID uuid = profile.id();
        String playerName = profile.name();
        
        if (SkinCache.getSkin(uuid) != null || !LOADING.add(uuid)) {
            return;
        }
        
        PlayerNameCache.cache(profile);
        
        ModConfig config = ModConfig.getInstance();
        ModConfig.PlayerOverride override = config.getPlayerOverride(playerName);
        
        CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<Void> loadingFuture;
                
                if (override != null && override.skipPremiumCheck) {
                    // 跳过正版检测，直接按 override 规则加载
                    loadingFuture = loadWithOverride(uuid, playerName, override, downloader);
                } else if (override != null) {
                    // 有 override 配置，检查正版状态后混合加载
                    MojangApiChecker.PlayerProfile playerProfile = MojangApiChecker.getPlayerProfile(playerName);
                    
                    if (playerProfile != null && playerProfile.isPremium && playerProfile.uuid != null) {
                        loadingFuture = loadMixed(uuid, playerProfile.uuid, playerName, override, downloader);
                    } else {
                        // 非正版但有 override，按 override 规则加载
                        loadingFuture = loadWithOverride(uuid, playerName, override, downloader);
                    }
                } else {
                    // 没有 override，按默认逻辑
                    MojangApiChecker.PlayerProfile playerProfile = MojangApiChecker.getPlayerProfile(playerName);
                    
                    if (playerProfile != null && playerProfile.isPremium && playerProfile.uuid != null) {
                        loadingFuture = loadPremiumSkins(uuid, playerProfile.uuid, playerName, downloader);
                    } else {
                        loadingFuture = loadCustomSkins(uuid, playerName, downloader);
                    }
                }
                
                loadingFuture.whenComplete((result, throwable) -> LOADING.remove(uuid));
                
            } catch (Exception e) {
                LOADING.remove(uuid);
            }
        });
    }
    
    private static CompletableFuture<Void> loadWithOverride(UUID uuid, String playerName, ModConfig.PlayerOverride override, SkinTextureDownloader downloader) {
        CompletableFuture<Void> skinFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> capeFuture = CompletableFuture.completedFuture(null);
        
        if (override.skin.useCustomApi) {
            skinFuture = loadCustomTexture(uuid, playerName, MinecraftProfileTexture.Type.SKIN, override.skin.apiIndex, downloader);
        }
        
        if (override.cape.useCustomApi) {
            capeFuture = loadCustomTexture(uuid, playerName, MinecraftProfileTexture.Type.CAPE, override.cape.apiIndex, downloader);
        }
        
        return CompletableFuture.allOf(skinFuture, capeFuture);
    }
    
    private static CompletableFuture<Void> loadMixed(UUID cacheUuid, UUID mojangUuid, String playerName, ModConfig.PlayerOverride override, SkinTextureDownloader downloader) {
        MojangSessionApi.ProfileTextures textures = MojangSessionApi.getProfileTextures(mojangUuid);
        
        CompletableFuture<Void> skinFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> capeFuture = CompletableFuture.completedFuture(null);
        
        if (override.skin.useCustomApi) {
            skinFuture = loadCustomTexture(cacheUuid, playerName, MinecraftProfileTexture.Type.SKIN, override.skin.apiIndex, downloader);
        } else if (textures != null && textures.skinUrl != null) {
            skinFuture = loadPremiumTexture(cacheUuid, mojangUuid, textures.skinUrl, MinecraftProfileTexture.Type.SKIN, downloader);
        }
        
        if (override.cape.useCustomApi) {
            capeFuture = loadCustomTexture(cacheUuid, playerName, MinecraftProfileTexture.Type.CAPE, override.cape.apiIndex, downloader);
        } else if (textures != null && textures.capeUrl != null) {
            capeFuture = loadPremiumTexture(cacheUuid, mojangUuid, textures.capeUrl, MinecraftProfileTexture.Type.CAPE, downloader);
        }
        
        return CompletableFuture.allOf(skinFuture, capeFuture);
    }
    
    private static CompletableFuture<Void> loadPremiumSkins(UUID cacheUuid, UUID mojangUuid, String playerName, SkinTextureDownloader downloader) {
        MojangSessionApi.ProfileTextures textures = MojangSessionApi.getProfileTextures(mojangUuid);
        
        if (textures == null) {
            // Mojang API 无法获取纹理，尝试从自定义 API 加载
            return loadCustomSkins(cacheUuid, playerName, downloader);
        }
        
        CompletableFuture<Void> skinFuture = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> capeFuture = CompletableFuture.completedFuture(null);
        
        if (textures.skinUrl != null) {
            skinFuture = loadPremiumTexture(cacheUuid, mojangUuid, textures.skinUrl, MinecraftProfileTexture.Type.SKIN, downloader);
        } else {
            // 正版没有皮肤，尝试从自定义 API 加载
            skinFuture = loadCustomTexture(cacheUuid, playerName, MinecraftProfileTexture.Type.SKIN, -1, downloader);
        }
        
        if (textures.capeUrl != null) {
            capeFuture = loadPremiumTexture(cacheUuid, mojangUuid, textures.capeUrl, MinecraftProfileTexture.Type.CAPE, downloader);
        } else {
            // 正版没有披风，尝试从自定义 API 加载（这是关键修复！）
            capeFuture = loadCustomTexture(cacheUuid, playerName, MinecraftProfileTexture.Type.CAPE, -1, downloader);
        }
        
        return CompletableFuture.allOf(skinFuture, capeFuture);
    }
    
    private static CompletableFuture<Void> loadPremiumTexture(UUID cacheUuid, UUID mojangUuid, String url, MinecraftProfileTexture.Type type, SkinTextureDownloader downloader) {
        String hash = Hashing.sha1().hashUnencodedChars(mojangUuid.toString() + "_premium_" + type.name()).toString();
        Identifier textureId = Identifier.withDefaultNamespace((type == MinecraftProfileTexture.Type.SKIN ? "skins/" : "capes/") + hash);
        Path cachePath = getTextureCachePath(hash, type);
        
        return downloader.downloadAndRegisterSkin(textureId, cachePath, url, type == MinecraftProfileTexture.Type.SKIN)
            .thenAccept(asset -> {
                if (asset != null) {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SkinCache.cacheSkin(cacheUuid, asset);
                        // 记录为正版皮肤
                        PlayerSkinSourceCache.setSource(cacheUuid, "正版");
                    } else {
                        SkinCache.cacheCape(cacheUuid, asset);
                    }
                }
            });
    }
    
    private static CompletableFuture<Void> loadCustomTexture(UUID uuid, String playerName, MinecraftProfileTexture.Type type, int apiIndex, SkinTextureDownloader downloader) {
        String hash = Hashing.sha1().hashUnencodedChars(playerName + "_" + type.name() + "_" + apiIndex).toString();
        Identifier textureId = Identifier.withDefaultNamespace((type == MinecraftProfileTexture.Type.SKIN ? "skins/" : "capes/") + hash);
        Path cachePath = getTextureCachePath(hash, type);
        
        CompletableFuture<ClientAsset.Texture> future;
        
        if (apiIndex >= 0) {
            future = CustomSkinLoader.loadCustomSkinFromIndex(uuid, playerName, downloader, cachePath, textureId, type, apiIndex);
        } else {
            future = CustomSkinLoader.loadCustomSkin(uuid, playerName, downloader, cachePath, textureId, type);
        }
        
        return future.thenAccept(asset -> {
            if (asset != null) {
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    SkinCache.cacheSkin(uuid, asset);
                } else {
                    SkinCache.cacheCape(uuid, asset);
                }
            }
        });
    }
    
    private static CompletableFuture<Void> loadCustomSkins(UUID uuid, String playerName, SkinTextureDownloader downloader) {
        CompletableFuture<Void> skinFuture = loadCustomTexture(uuid, playerName, MinecraftProfileTexture.Type.SKIN, -1, downloader);
        CompletableFuture<Void> capeFuture = loadCustomTexture(uuid, playerName, MinecraftProfileTexture.Type.CAPE, -1, downloader);
        return CompletableFuture.allOf(skinFuture, capeFuture);
    }
    
    private static Path getTextureCachePath(String hash, MinecraftProfileTexture.Type type) {
        String folder = type == MinecraftProfileTexture.Type.SKIN ? "skins" : "capes";
        return Path.of(System.getProperty("java.io.tmpdir"), "wskinloader", folder, hash.substring(0, 2), hash);
    }
}
