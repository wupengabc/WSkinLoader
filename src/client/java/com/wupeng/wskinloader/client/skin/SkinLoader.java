package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import com.wupeng.wskinloader.client.util.BoundedCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * 统一的皮肤加载工具类
 */
public class SkinLoader {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("wskinloader");

    private static final LoadTracker LOADS = new LoadTracker();
    private static final ResolutionCache RESOLUTIONS = new ResolutionCache(4096, System::currentTimeMillis);
    private static final Map<UUID, Boolean> REFRESH = new BoundedCache<>(8192);
    private static final java.util.LinkedHashMap<UUID, Boolean> RECENT = new java.util.LinkedHashMap<>(16, 0.75f, true);
    private static final int MAX_CACHED_PLAYERS = 4096;
    private static boolean stopped;
    private static final long FAILURE_COOLDOWN_MS = 30000L;
    private static final long MISSING_TEXTURE_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final ExecutorService NETWORK_EXECUTOR = new ThreadPoolExecutor(4, 4, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(1024), runnable -> {
        Thread thread = new Thread(runnable, "WSkinLoader-Network");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    private record Load(UUID uuid, long token, ModConfig.ConfigData config, boolean refresh,
                        java.util.concurrent.atomic.AtomicBoolean degraded) {
        boolean current() { return LOADS.isCurrent(uuid, token); }
        boolean publish(Runnable action) { return LOADS.publish(uuid, token, action); }
    }
    
    public static void loadSkinForProfile(GameProfile profile, SkinTextureDownloader downloader) {
        if (profile == null || profile.id() == null || profile.name() == null || profile.name().isBlank()) {
            return;
        }
        
        UUID uuid = profile.id();
        String playerName = profile.name();
        
        final Load load;
        synchronized (LOADS) {
            if (stopped) return;
            RECENT.put(uuid, Boolean.TRUE);
            while (RECENT.size() > MAX_CACHED_PLAYERS) {
                UUID eldest = RECENT.keySet().iterator().next();
                RECENT.remove(eldest);
                LOADS.invalidate(eldest, () -> {
                    SkinCache.removePlayer(eldest);
                    PlayerSkinSourceCache.remove(eldest);
                    RESOLUTIONS.remove(eldest);
                    REFRESH.remove(eldest);
                    SafeTextureDownloader.release(eldest);
                });
            }
            PlayerNameCache.cache(profile);
            if (RESOLUTIONS.blocksLoad(uuid)) return;
            long token = LOADS.begin(uuid);
            if (token < 0) return;
            try {
                load = new Load(uuid, token, ModConfig.getInstance().deepCopy(), REFRESH.remove(uuid) != null,
                        new java.util.concurrent.atomic.AtomicBoolean());
            } catch (RuntimeException e) {
                LOADS.finish(uuid, token, () -> RESOLUTIONS.remember(uuid, FAILURE_COOLDOWN_MS));
                LOGGER.warn("[WSkinLoader] Unable to snapshot skin configuration", e);
                return;
            }
        }
        ModConfig.PlayerOverride override = load.config.playerOverrides.get(playerName);

        // Player mapping only changes which name each texture is looked up by; the
        // override rules (skip premium / custom API / index) stay exactly the same.
        String skinName = resolveSourceName(override, true, playerName);
        String capeName = resolveSourceName(override, false, playerName);

        try {
            CompletableFuture.runAsync(() -> {
            try {
                if (!load.current()) return;
                loadResolved(load, skinName, capeName, override, downloader)
                        .whenComplete((result, throwable) -> finish(load, throwable));
                
            } catch (Exception e) {
                finish(load, e);
            }
            }, NETWORK_EXECUTOR);
        } catch (RuntimeException e) {
            finish(load, e);
        }
    }

    private static void finish(Load load, Throwable error) {
        LOADS.finish(load.uuid, load.token, () -> {
            if (error != null || load.degraded.get()) {
                if (load.refresh) REFRESH.put(load.uuid, Boolean.TRUE);
                RESOLUTIONS.remember(load.uuid, isOverloaded(error) ? 1000L : FAILURE_COOLDOWN_MS);
                if (error != null) LOGGER.debug("[WSkinLoader] Texture load failed for {}", load.uuid, error);
            } else {
                boolean missing = !SkinCache.hasSkin(load.uuid) || !SkinCache.hasCape(load.uuid);
                RESOLUTIONS.remember(load.uuid, missing ? MISSING_TEXTURE_TTL_MS : Long.MAX_VALUE);
            }
        });
    }

    private static boolean isOverloaded(Throwable error) {
        while (error != null) {
            if (error instanceof java.util.concurrent.RejectedExecutionException) return true;
            error = error.getCause();
        }
        return false;
    }

    /** Keeps rendered players recent and restores evicted entries from global disk cache. */
    public static void ensureLoaded(UUID uuid) {
        if (uuid == null) return;
        synchronized (LOADS) {
            if (RECENT.containsKey(uuid)) RECENT.get(uuid);
            if (RESOLUTIONS.blocksLoad(uuid)) return;
        }
        String name = PlayerNameCache.getName(uuid);
        if (name == null) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.getConnection() != null) {
                var info = minecraft.getConnection().getPlayerInfo(uuid);
                if (info != null) name = info.getProfile().name();
            }
        }
        triggerLoad(uuid, name);
    }

    public static void ensureLoaded(GameProfile profile) {
        if (profile == null) return;
        PlayerNameCache.cache(profile);
        ensureLoaded(profile.id());
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


    /**
     * Shuts down the network executor gracefully.
     * Should be called when the mod is being unloaded.
     */
    public static void shutdown() {
        LOGGER.info("[WSkinLoader] 正在关闭网络线程池...");
        LOADS.invalidateAll(() -> {
            stopped = true;
            SkinCache.clear();
            SafeTextureDownloader.releaseAll();
        });
        SafeTextureDownloader.shutdown();
        NETWORK_EXECUTOR.shutdown();
        try {
            if (!NETWORK_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                LOGGER.warn("[WSkinLoader] 网络线程池未能在5秒内关闭，强制关闭");
                NETWORK_EXECUTOR.shutdownNow();
                if (!NETWORK_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.error("[WSkinLoader] 无法关闭网络线程池");
                }
            }
        } catch (InterruptedException e) {
            LOGGER.error("[WSkinLoader] 关闭网络线程池时被中断", e);
            NETWORK_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Drops every cached/loading state for one player so the next render fetches
     * the skin again.
     *
     * <p>Only explicit refreshes bypass the global disk cache. Joining a server
     * does not invalidate cached textures.
     */
    public static void invalidate(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LOADS.invalidate(uuid, () -> {
            SkinCache.removePlayer(uuid);
            PlayerSkinSourceCache.remove(uuid);
            RESOLUTIONS.remove(uuid);
            REFRESH.put(uuid, Boolean.TRUE);
            SafeTextureDownloader.release(uuid);
        });
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
        // Capture names so historical players can bypass disk cache on their next load.
        // Names come from both the cached textures and the name cache, because a
        // player whose rules previously resolved to nothing has no texture cache
        // entry and would otherwise never be re-resolved.
        Map<UUID, String> names = new java.util.HashMap<>(PlayerNameCache.snapshot());
        for (UUID id : SkinCache.getCachedPlayers()) {
            names.putIfAbsent(id, PlayerNameCache.getName(id));
        }

        LOADS.invalidateAll(() -> {
            SkinCache.clear();
            PlayerSkinSourceCache.clear();
            RESOLUTIONS.clear();
            names.keySet().forEach(uuid -> REFRESH.put(uuid, Boolean.TRUE));
            SafeTextureDownloader.releaseAll();
        });

        // Reload visible players first; historical global entries reload lazily
        // when rendered rather than flooding the bounded network queue.
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

    /**
     * SHA-1 of the string, hex encoded.
     *
     * <p>The hash is a cache key, not a security boundary. The digest must stay
     * SHA-1 <em>and</em> keep using UTF-16LE bytes, because the previous
     * implementation was Guava's {@code Hashing.sha1().hashUnencodedChars(...)},
     * which hashes each char as a little-endian 16-bit unit. Hashing UTF-8 bytes
     * instead would change every hash and orphan all existing cache files and
     * texture ids on upgrade.
     *
     * <p>{@code Hashing.sha1()} is deprecated upstream, so the JDK digest is
     * used directly while preserving the exact byte layout.
     */
    private static String sha1Hex(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16));
                builder.append(Character.forDigit(b & 0xF, 16));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // SHA-1 is required of every Java platform, so this cannot happen.
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    
    /**
     * Loads skin and cape by their resolved source names, applying the override
     * rules unchanged.
     */
    private static CompletableFuture<Void> loadResolved(Load load, String skinName, String capeName,
                                                       ModConfig.PlayerOverride override,
                                                       SkinTextureDownloader downloader) {
        boolean skipPremium = override != null && override.skipPremiumCheck;
        ModConfig.ConfigData config = load.config;
        boolean premiumFirst = config.premiumFirst;
        boolean keepPremium = config.keepPremiumWhenUnavailable;

        CompletableFuture<Void> skinFuture = thisTextureFuture(load, skinName,
                override != null && override.skin.useCustomApi,
                override == null ? -1 : override.skin.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.SKIN, downloader);

        CompletableFuture<Void> capeFuture = thisTextureFuture(load, capeName,
                override != null && override.cape.useCustomApi,
                override == null ? -1 : override.cape.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.CAPE, downloader);

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
     *   <li>{@code premiumFirst} disabled - skip the Mojang lookup entirely and
     *       read from the custom APIs (same as {@code skipPremiumCheck}).</li>
     *   <li>otherwise - load the premium texture, falling back to the custom APIs
     *       (same {@code apiIndex}) when that account has no such texture.</li>
     * </ul>
     */
    private static CompletableFuture<Void> thisTextureFuture(Load load, String sourceName,
                                                             boolean useCustomApi, int apiIndex,
                                                             boolean skipPremium,
                                                             boolean premiumFirst,
                                                             boolean keepPremiumWhenUnavailable,
                                                              MinecraftProfileTexture.Type type,
                                                              SkinTextureDownloader downloader) {
        if (useCustomApi || skipPremium || !premiumFirst) {
            return loadCustomTexture(load, sourceName, type, apiIndex, downloader);
        }
        return loadPremiumTextureByName(load, sourceName, type, apiIndex, keepPremiumWhenUnavailable, downloader);
    }

    /**
     * Resolves the premium texture of {@code sourceName} and, when that account has
     * no such texture, falls back to the configured custom APIs using
     * {@code apiIndex} (-1 = try every API in order).
     *
     * <p>When {@code keepPremiumWhenUnavailable} is enabled and the Mojang session
     * server cannot be reached, no texture is loaded instead of falling back to a
     * third-party one, so a premium skin is never silently replaced.
     */
    private static CompletableFuture<Void> loadPremiumTextureByName(Load load, String sourceName,
                                                                    MinecraftProfileTexture.Type type,
                                                                    int apiIndex,
                                                                    boolean keepPremiumWhenUnavailable,
                                                                    SkinTextureDownloader downloader) {
        try {
            var texture = PremiumTextureResolver.resolve(sourceName, type, keepPremiumWhenUnavailable,
                    MojangApiChecker::getPlayerProfile, MojangSessionApi::getProfileTextures);
            if (texture == null) return loadCustomTexture(load, sourceName, type, apiIndex, downloader);
            if (texture.url() == null) {
                load.degraded.set(true);
                return loadCustomTexture(load, sourceName, type, apiIndex, downloader);
            }
            return loadPremiumTexture(load, texture.uuid(), texture.url(), type, downloader,
                    texture.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        } catch (java.io.IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    
    private static CompletableFuture<Void> loadPremiumTexture(Load load, UUID mojangUuid, String url,
            MinecraftProfileTexture.Type type, SkinTextureDownloader downloader, PlayerModelType model) {
        String hash = sha1Hex(
                mojangUuid.toString() + "_premium_" + type.name() + "_" + url);
        Identifier textureId = Identifier.fromNamespaceAndPath("wskinloader",
                (type == MinecraftProfileTexture.Type.SKIN ? "skins/" : "capes/") + load.uuid + "_" + load.token);
        Path cachePath = getTextureCachePath(hash, type);
        
        return SafeTextureDownloader.download(downloader, textureId, cachePath, url,
                type == MinecraftProfileTexture.Type.SKIN, load.refresh, load::current)
                .thenCompose(asset -> publishTexture(load, asset, type, model, "正版", downloader));
    }
    
    private static CompletableFuture<Void> loadCustomTexture(Load load, String playerName,
            MinecraftProfileTexture.Type type, int apiIndex, SkinTextureDownloader downloader) {
        var apis = type == MinecraftProfileTexture.Type.SKIN ? load.config.skinApis : load.config.capeApis;
        String hash = sha1Hex(playerName + "_" + type.name());
        Identifier textureId = Identifier.fromNamespaceAndPath("wskinloader",
                (type == MinecraftProfileTexture.Type.SKIN ? "skins/" : "capes/") + load.uuid + "_" + load.token);
        Path cachePath = getTextureCachePath(hash, type);

        return CustomSkinLoader.load(apis, playerName, downloader, cachePath, textureId,
                type, apiIndex, load.refresh, load::current).thenCompose(result -> result == null
                ? CompletableFuture.completedFuture(null)
                : publishTexture(load, result.asset(), type, result.model(), result.sourceAlias(), downloader));
    }

    private static CompletableFuture<Void> publishTexture(Load load, net.minecraft.core.ClientAsset.Texture asset,
            MinecraftProfileTexture.Type type, PlayerModelType model, String source, SkinTextureDownloader downloader) {
        if (asset == null) return CompletableFuture.completedFuture(null);
        try {
            return CompletableFuture.runAsync(() -> {
                boolean accepted = load.publish(() -> {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SkinCache.cacheSkin(load.uuid, asset);
                        if (model != null) SkinCache.cacheModel(load.uuid, model);
                        PlayerSkinSourceCache.setSource(load.uuid, source);
                    } else {
                        SkinCache.cacheCape(load.uuid, asset);
                    }
                    SafeTextureDownloader.accept(asset);
                });
                if (!accepted) SafeTextureDownloader.discard(asset);
            }, ((com.wupeng.wskinloader.client.mixin.SkinTextureDownloaderAccessor) downloader).wskinloader$getMainThreadExecutor())
            .whenComplete((ignored, error) -> {
                if (error != null) SafeTextureDownloader.discard(asset);
            });
        } catch (RuntimeException e) {
            SafeTextureDownloader.discard(asset);
            return CompletableFuture.failedFuture(e);
        }
    }

    
    private static Path getTextureCachePath(String hash, MinecraftProfileTexture.Type type) {
        String folder = type == MinecraftProfileTexture.Type.SKIN ? "skins" : "capes";
        return FabricLoader.getInstance().getGameDir().resolve("cache").resolve("wskinloader")
            .resolve(folder).resolve(hash.substring(0, 2)).resolve(hash);
    }
}
