package com.wupeng.wskinloader.client.skin;

import com.google.common.hash.Hashing;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

/**
 * 统一的皮肤加载工具类
 */
public class SkinLoader {
    
    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> FAILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> INVALIDATION = new ConcurrentHashMap<>();
    private static final long FAILURE_COOLDOWN_MS = 30000L;
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "WSkinLoader-Network");
        thread.setDaemon(true);
        return thread;
    });
    
    public static void loadSkinForProfile(GameProfile profile, SkinTextureDownloader downloader) {
        if (profile == null || profile.id() == null || profile.name() == null) {
            return;
        }
        
        UUID uuid = profile.id();
        String playerName = profile.name();
        
        if (SkinCache.getSkin(uuid) != null || !LOADING.add(uuid)) {
            return;
        }

        Long failedAt = FAILED.get(uuid);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_COOLDOWN_MS) {
            LOADING.remove(uuid);
            return;
        }
        
        PlayerNameCache.cache(profile);

        ModConfig config = ModConfig.getInstance();
        ModConfig.PlayerOverride override = config.getPlayerOverride(playerName);

        // Player mapping only changes which name each texture is looked up by; the
        // override rules (skip premium / custom API / index) stay exactly the same.
        String skinName = resolveSourceName(override, true, playerName);
        String capeName = resolveSourceName(override, false, playerName);

        try {
            CompletableFuture.runAsync(() -> {
            try {
                CompletableFuture<Void> loadingFuture =
                        loadResolved(uuid, skinName, capeName, override, downloader);

                loadingFuture.whenComplete((result, throwable) -> {
                    LOADING.remove(uuid);
                    if (throwable != null || (SkinCache.getSkin(uuid) == null && SkinCache.getCape(uuid) == null)) {
                        FAILED.put(uuid, System.currentTimeMillis());
                    } else {
                        FAILED.remove(uuid);
                    }
                });
                
            } catch (Exception e) {
                LOADING.remove(uuid);
                FAILED.put(uuid, System.currentTimeMillis());
            }
            }, NETWORK_EXECUTOR);
        } catch (RuntimeException e) {
            LOADING.remove(uuid);
            FAILED.put(uuid, System.currentTimeMillis());
        }
    }

    /** Resolves the player name a texture should be loaded from (player mapping). */
    public static String resolveSourceName(ModConfig.PlayerOverride override, boolean skin, String playerName) {
        if (override == null) {
            return playerName;
        }
        String mapped = skin ? override.skinSourcePlayer : override.capeSourcePlayer;
        if (mapped == null || mapped.isBlank()) {
            return playerName;
        }
        return mapped.trim();
    }


    public static void clearLoadingState() {
        LOADING.clear();
        FAILED.clear();
    }

    /**
     * Drops every cached/loading state for one player so the next render fetches
     * the skin again.
     *
     * <p>The invalidation nonce is part of the texture hash, so a re-fetch also
     * produces a new texture id and a new cache file. Without it the previous
     * (possibly stale) texture would be re-registered from the on-disk cache and
     * the player would keep the old skin.
     */
    public static void invalidate(UUID uuid) {
        if (uuid == null) {
            return;
        }
        SkinCache.removePlayer(uuid);
        PlayerSkinSourceCache.remove(uuid);
        PlayerNameCache.remove(uuid);
        FAILED.remove(uuid);
        LOADING.remove(uuid);
        INVALIDATION.put(uuid, System.currentTimeMillis());
    }

    /**
     * Drops one player's cache and immediately starts loading again.
     *
     * <p>Clearing the cache alone is not enough: {@code loadSkinForProfile} is
     * only invoked from vanilla's skin resolution paths, and vanilla memoizes the
     * resolved skin per profile ({@code SkinManager.skinCache} plus the captured
     * {@code Supplier<PlayerSkin>} in {@code createLookup}), so those paths are
     * not re-entered for a player that is already known. Re-triggering the load
     * here is what makes a deleted cache actually refresh.
     */
    public static void reload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        // Read the name before invalidate() clears the name cache.
        reload(uuid, PlayerNameCache.getName(uuid));
    }

    /**
     * Same as {@link #reload(UUID)} but with a caller-supplied player name, for
     * callers that know the name even when the name cache is still empty.
     */
    public static void reload(UUID uuid, String playerName) {
        if (uuid == null) {
            return;
        }
        invalidate(uuid);
        triggerLoad(uuid, playerName);
    }

    public static void invalidateAll() {
        // Capture names before dropping them so the reload can still be started.
        // Names come from both the cached textures and the name cache, because a
        // player whose rules previously resolved to nothing has no texture cache
        // entry and would otherwise never be re-resolved.
        Map<UUID, String> names = new java.util.HashMap<>(PlayerNameCache.snapshot());
        for (UUID id : SkinCache.getCachedPlayers()) {
            names.putIfAbsent(id, PlayerNameCache.getName(id));
        }

        SkinCache.clear();
        PlayerSkinSourceCache.clear();
        PlayerNameCache.clear();
        FAILED.clear();
        LOADING.clear();
        INVALIDATION.clear();
        INVALIDATION.put(GLOBAL_INVALIDATION_KEY, System.currentTimeMillis());

        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            triggerLoad(entry.getKey(), entry.getValue());
        }

        // Also re-resolve every player currently in the world, so a changed rule
        // (e.g. toggling the premium check) refreshes them without a relog.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (com.mojang.authlib.GameProfile profile : minecraft.getConnection().getOnlinePlayers().stream()
                    .map(net.minecraft.client.multiplayer.PlayerInfo::getProfile)
                    .toList()) {
                triggerLoad(profile.id(), profile.name());
            }
        }
    }

    /** Starts a load using vanilla's texture downloader, if one is available. */
    private static void triggerLoad(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        SkinManager skinManager = minecraft.getSkinManager();
        if (!(skinManager instanceof com.wupeng.wskinloader.client.mixin.PlayerSkinProviderAccessor accessor)) {
            return;
        }
        loadSkinForProfile(new GameProfile(uuid, name), accessor.getDownloader());
    }

    /** Nonce mixed into texture hashes; changes when a cache entry is dropped. */
    private static final UUID GLOBAL_INVALIDATION_KEY = new UUID(0L, 0L);

    private static String invalidationNonce(UUID uuid) {
        Long perPlayer = INVALIDATION.get(uuid);
        Long global = INVALIDATION.get(GLOBAL_INVALIDATION_KEY);
        long value = Math.max(perPlayer != null ? perPlayer : 0L, global != null ? global : 0L);
        return Long.toString(value);
    }

    
    /**
     * Loads skin and cape by their resolved source names, applying the override
     * rules unchanged.
     */
    private static CompletableFuture<Void> loadResolved(UUID cacheUuid, String skinName, String capeName,
                                                       ModConfig.PlayerOverride override,
                                                       SkinTextureDownloader downloader) {
        boolean skipPremium = override != null && override.skipPremiumCheck;

        CompletableFuture<Void> skinFuture = thisTextureFuture(cacheUuid, skinName,
                override != null && override.skin.useCustomApi,
                override == null ? -1 : override.skin.apiIndex,
                skipPremium, MinecraftProfileTexture.Type.SKIN, downloader);

        CompletableFuture<Void> capeFuture = thisTextureFuture(cacheUuid, capeName,
                override != null && override.cape.useCustomApi,
                override == null ? -1 : override.cape.apiIndex,
                skipPremium, MinecraftProfileTexture.Type.CAPE, downloader);

        return CompletableFuture.allOf(skinFuture, capeFuture);
    }

    /**
     * Resolves one texture using exactly the same rules as the outer config.
     * Player mapping only changes the name the texture is looked up by.
     *
     * <ul>
     *   <li>{@code useCustomApi} - always load from the configured custom APIs,
     *       using {@code apiIndex} (-1 = try each API in order).</li>
     *   <li>{@code skipPremiumCheck} without a custom API - skip the Mojang lookup
     *       and read from the custom APIs instead.</li>
     *   <li>otherwise - load the premium texture, falling back to the custom APIs
     *       (same {@code apiIndex}) when that account has no such texture.</li>
     * </ul>
     */
    private static CompletableFuture<Void> thisTextureFuture(UUID cacheUuid, String sourceName,
                                                             boolean useCustomApi, int apiIndex,
                                                             boolean skipPremium,
                                                             MinecraftProfileTexture.Type type,
                                                             SkinTextureDownloader downloader) {
        if (useCustomApi || skipPremium) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, downloader);
        }
        return loadPremiumTextureByName(cacheUuid, sourceName, type, apiIndex, downloader);
    }

    /**
     * Resolves the premium texture of {@code sourceName} and, when that account has
     * no such texture, falls back to the configured custom APIs using
     * {@code apiIndex} (-1 = try every API in order).
     */
    private static CompletableFuture<Void> loadPremiumTextureByName(UUID cacheUuid, String sourceName,
                                                                    MinecraftProfileTexture.Type type,
                                                                    int apiIndex,
                                                                    SkinTextureDownloader downloader) {
        MojangApiChecker.PlayerProfile profile = MojangApiChecker.getPlayerProfile(sourceName);
        if (profile == null || !profile.isPremium || profile.uuid == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, downloader);
        }

        MojangSessionApi.ProfileTextures textures = MojangSessionApi.getProfileTextures(profile.uuid);
        if (textures == null) {
            // Mojang 服务不可用时不要用第三方皮肤覆盖正版皮肤
            return CompletableFuture.completedFuture(null);
        }

        String url = type == MinecraftProfileTexture.Type.SKIN ? textures.skinUrl : textures.capeUrl;
        if (url == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, downloader);
        }

        if (type == MinecraftProfileTexture.Type.SKIN) {
            SkinCache.cacheModel(cacheUuid, textures.isSlim ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        }
        return loadPremiumTexture(cacheUuid, profile.uuid, url, type, downloader);
    }

    
    private static CompletableFuture<Void> loadPremiumTexture(UUID cacheUuid, UUID mojangUuid, String url, MinecraftProfileTexture.Type type, SkinTextureDownloader downloader) {
        String hash = Hashing.sha1().hashUnencodedChars(
                mojangUuid.toString() + "_premium_" + type.name() + "_" + url + "_" + invalidationNonce(cacheUuid)).toString();
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
        ModConfig config = ModConfig.getInstance();
        String apiFingerprint = (type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis).stream()
            .map(api -> api.url == null ? "" : api.url)
            .collect(java.util.stream.Collectors.joining("\u0000"));
        // The source name is part of the key: player mapping loads another
        // player's texture into this cache entry, so a changed mapping must map
        // to a different texture id and cache file.
        String hash = Hashing.sha1().hashUnencodedChars(
                uuid + "_" + playerName + "_" + type.name() + "_" + apiIndex + "_" + apiFingerprint + "_" + invalidationNonce(uuid)).toString();
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
    
    private static Path getTextureCachePath(String hash, MinecraftProfileTexture.Type type) {
        String folder = type == MinecraftProfileTexture.Type.SKIN ? "skins" : "capes";
        return FabricLoader.getInstance().getGameDir().resolve("cache").resolve("wskinloader")
            .resolve(folder).resolve(hash.substring(0, 2)).resolve(hash);
    }
}
