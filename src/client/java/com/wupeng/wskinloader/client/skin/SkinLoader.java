package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.HttpTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 统一的皮肤加载工具类（1.16.5 版）。
 *
 * <p>Downloads and validates images in the background, then registers textures
 * on the client thread. Only successfully decoded textures enter SkinCache.
 */
public class SkinLoader {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("wskinloader");

    private static final Map<UUID, Long> LOADING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FAILED = new ConcurrentHashMap<>();
    private static final AtomicLong REQUEST_SEQUENCE = new AtomicLong();
    private static final AtomicLong INVALIDATION_SEQUENCE = new AtomicLong();
    private static final Map<UUID, Long> INVALIDATION = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, File> REGISTERED_FILES = new ConcurrentHashMap<>();
    private static final UUID GLOBAL_INVALIDATION_KEY = new UUID(0L, 0L);
    private static final long FAILURE_COOLDOWN_MS = 30000L;
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "WSkinLoader-Network");
        thread.setDaemon(true);
        return thread;
    });

    public static void loadSkinForProfile(GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null
                || profile.getName().trim().isEmpty()) {
            return;
        }

        UUID uuid = profile.getId();
        String playerName = profile.getName().trim();

        // 尽早缓存 uuid -> 名字，否则下面的早退会让已缓存皮肤的玩家再也
        // 写不进名字，缓存页只能显示 UUID。
        PlayerNameCache.cache(profile);

        if (SkinCache.getSkin(uuid) != null) {
            return;
        }

        Long failedAt = FAILED.get(uuid);
        if (failedAt != null && System.currentTimeMillis() - failedAt < FAILURE_COOLDOWN_MS) {
            return;
        }

        final long request = REQUEST_SEQUENCE.incrementAndGet();
        if (LOADING.putIfAbsent(uuid, request) != null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        ModConfig.PlayerOverride override = config.getPlayerOverride(playerName);

        String skinName = resolveSourceName(override, true, playerName);
        String capeName = resolveSourceName(override, false, playerName);

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    CompletableFuture<Void> loadingFuture =
                            loadResolved(uuid, skinName, capeName, override, request);

                    loadingFuture.whenComplete((result, throwable) ->
                            Minecraft.getInstance().execute(() -> finishLoading(uuid, request, throwable)));
                } catch (Exception e) {
                    Minecraft.getInstance().execute(() -> finishLoading(uuid, request, e));
                }
            }, NETWORK_EXECUTOR);
        } catch (RuntimeException e) {
            finishLoading(uuid, request, e);
        }
    }

    private static boolean isCurrent(UUID uuid, long request) {
        return Long.valueOf(request).equals(LOADING.get(uuid));
    }

    private static void finishLoading(UUID uuid, long request, Throwable throwable) {
        if (!LOADING.remove(uuid, request)) {
            return;
        }
        if (throwable != null || SkinCache.getSkin(uuid) == null) {
            FAILED.put(uuid, System.currentTimeMillis());
        } else {
            FAILED.remove(uuid);
        }
    }

    /** Resolves the player name a texture should be loaded from (player mapping). */
    public static String resolveSourceName(ModConfig.PlayerOverride override, boolean skin, String playerName) {
        if (override == null) {
            return playerName;
        }
        String mapped = skin ? override.skinSourcePlayer : override.capeSourcePlayer;
        if (mapped == null || mapped.trim().isEmpty()) {
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
     */
    public static void invalidate(UUID uuid) {
        if (uuid == null) {
            return;
        }
        LOADING.remove(uuid);
        INVALIDATION.put(uuid, nextInvalidation());
        SkinCache.removePlayer(uuid);
        PlayerSkinSourceCache.remove(uuid);
        FAILED.remove(uuid);
    }

    /**
     * Drops one player's cache and immediately starts loading again.
     */
    public static void reload(UUID uuid) {
        if (uuid == null) {
            return;
        }
        reload(uuid, PlayerNameCache.getName(uuid));
    }

    public static void reload(UUID uuid, String playerName) {
        if (uuid == null) {
            return;
        }
        invalidate(uuid);
        triggerLoad(uuid, playerName);
    }

    public static void invalidateAll() {
        Map<UUID, String> names = new java.util.HashMap<>(PlayerNameCache.snapshot());
        for (UUID id : SkinCache.getCachedPlayers()) {
            names.putIfAbsent(id, PlayerNameCache.getName(id));
        }

        LOADING.clear();
        INVALIDATION.clear();
        INVALIDATION.put(GLOBAL_INVALIDATION_KEY, nextInvalidation());
        SkinCache.clear();
        PlayerSkinSourceCache.clear();
        FAILED.clear();

        for (Map.Entry<UUID, String> entry : names.entrySet()) {
            triggerLoad(entry.getKey(), entry.getValue());
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (com.mojang.authlib.GameProfile profile : minecraft.getConnection().getOnlinePlayers().stream()
                    .map(net.minecraft.client.multiplayer.PlayerInfo::getProfile)
                    .collect(java.util.stream.Collectors.toList())) {
                triggerLoad(profile.getId(), profile.getName());
            }
        }
    }

    private static void triggerLoad(UUID uuid, String name) {
        if (uuid == null || name == null || name.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getConnection() == null) {
            return;
        }
        loadSkinForProfile(new GameProfile(uuid, name));
    }

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
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    private static long nextInvalidation() {
        return INVALIDATION_SEQUENCE.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()));
    }

    private static long invalidationNonce(UUID uuid) {
        return Math.max(INVALIDATION.getOrDefault(uuid, 0L),
                INVALIDATION.getOrDefault(GLOBAL_INVALIDATION_KEY, 0L));
    }

    private static CompletableFuture<Void> loadResolved(UUID cacheUuid, String skinName, String capeName,
                                                       ModConfig.PlayerOverride override, long request) {
        boolean skipPremium = override != null && override.skipPremiumCheck;
        ModConfig config = ModConfig.getInstance();
        boolean premiumFirst = config.premiumFirst;
        boolean keepPremium = config.keepPremiumWhenUnavailable;

        CompletableFuture<Void> skinFuture = thisTextureFuture(cacheUuid, skinName,
                override != null && override.skin.useCustomApi,
                override == null ? -1 : override.skin.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.SKIN, request);

        CompletableFuture<Void> capeFuture = thisTextureFuture(cacheUuid, capeName,
                override != null && override.cape.useCustomApi,
                override == null ? -1 : override.cape.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.CAPE, request);

        return CompletableFuture.allOf(skinFuture, capeFuture);
    }

    private static CompletableFuture<Void> thisTextureFuture(UUID cacheUuid, String sourceName,
                                                             boolean useCustomApi, int apiIndex,
                                                             boolean skipPremium,
                                                             boolean premiumFirst,
                                                             boolean keepPremiumWhenUnavailable,
                                                             MinecraftProfileTexture.Type type, long request) {
        if (useCustomApi || skipPremium || !premiumFirst) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, request);
        }
        return loadPremiumTextureByName(cacheUuid, sourceName, type, apiIndex, keepPremiumWhenUnavailable, request);
    }

    private static CompletableFuture<Void> loadPremiumTextureByName(UUID cacheUuid, String sourceName,
                                                                    MinecraftProfileTexture.Type type,
                                                                    int apiIndex,
                                                                    boolean keepPremiumWhenUnavailable, long request) {
        MojangApiChecker.PlayerProfile profile = MojangApiChecker.getPlayerProfile(sourceName);
        if (profile == null || !profile.isPremium || profile.uuid == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, request);
        }

        MojangSessionApi.ProfileTextures textures = MojangSessionApi.getProfileTextures(profile.uuid);
        if (textures == null) {
            if (keepPremiumWhenUnavailable) {
                return CompletableFuture.completedFuture(null);
            }
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, request);
        }

        String url = type == MinecraftProfileTexture.Type.SKIN ? textures.skinUrl : textures.capeUrl;
        if (url == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, request);
        }

        MinecraftProfileTexture texture = new MinecraftProfileTexture(url, null);
        return register(cacheUuid, texture, type, "正版", textures.isSlim ? "slim" : "default", request)
                .handle((value, error) -> {
                    if (error != null && !keepPremiumWhenUnavailable && isCurrent(cacheUuid, request)) {
                        return loadCustomTexture(cacheUuid, sourceName, type, apiIndex, request);
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                }).thenCompose(future -> future);
    }

    private static CompletableFuture<Void> loadCustomTexture(UUID uuid, String playerName,
                                                            MinecraftProfileTexture.Type type, int apiIndex, long request) {
        return loadCustomTexture(uuid, playerName, type, apiIndex, apiIndex >= 0 ? apiIndex : 0, request);
    }

    private static CompletableFuture<Void> loadCustomTexture(UUID uuid, String playerName,
                                                            MinecraftProfileTexture.Type type, int apiIndex,
                                                            int index, long request) {
        ModConfig config = ModConfig.getInstance();
        int size = (type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis).size();
        if (!isCurrent(uuid, request) || index >= size) {
            return CompletableFuture.completedFuture(null);
        }
        return CustomSkinLoader.loadCustomSkinFromIndex(uuid, playerName, type, index)
                .thenCompose(texture -> texture == null
                        ? CompletableFuture.<Void>completedFuture(null)
                        : register(uuid, texture, type, texture.getMetadata("wskinloader_source"), null, request))
                .handle((value, error) -> {
                    boolean loaded = type == MinecraftProfileTexture.Type.SKIN
                            ? SkinCache.getSkin(uuid) != null : SkinCache.getCape(uuid) != null;
                    if (!loaded && apiIndex < 0 && isCurrent(uuid, request)) {
                        return loadCustomTexture(uuid, playerName, type, apiIndex, index + 1, request);
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                }).thenCompose(future -> future);
    }

    /**
     * A ResourceLocation alone is not proof of a successful HTTP download.
     * Validate the file first, then cache only after HttpTexture decoded it.
     */
    private static CompletableFuture<Void> register(UUID uuid, MinecraftProfileTexture texture,
                                                    MinecraftProfileTexture.Type type, String source,
                                                    String model, long request) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || !isCurrent(uuid, request)) {
            result.complete(null);
            return result;
        }
        final long nonce = invalidationNonce(uuid);
        final String hash = sha1Hex(uuid + "_" + type + "_" + texture.getUrl() + "_" + nonce);
        CompletableFuture.supplyAsync(() -> {
            try {
                File dir = locateSkinsDirectory();
                if (dir == null) {
                    throw new java.io.IOException("Skin cache directory unavailable");
                }
                File file = new File(new File(dir, hash.substring(0, 2)), hash);
                return TextureDownloader.download(texture.getUrl(), file,
                        type == MinecraftProfileTexture.Type.SKIN, minecraft.getProxy());
            } catch (java.io.IOException e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, NETWORK_EXECUTOR).whenComplete((file, error) -> minecraft.execute(() -> {
            try {
                if (!isCurrent(uuid, request)) {
                    if (file != null && nonce != invalidationNonce(uuid)) {
                        java.nio.file.Files.deleteIfExists(file.toPath());
                    }
                    result.complete(null);
                    return;
                }
                if (error != null) {
                    throw new java.util.concurrent.CompletionException(error);
                }
                ResourceLocation location = new ResourceLocation("wskinloader", "skins/" + uuid + "/"
                        + type.name().toLowerCase(java.util.Locale.ROOT));
                AtomicBoolean decoded = new AtomicBoolean();
                HttpTexture httpTexture = new HttpTexture(file, texture.getUrl(), DefaultPlayerSkin.getDefaultSkin(),
                        type == MinecraftProfileTexture.Type.SKIN, () -> decoded.set(true));
                minecraft.getTextureManager().register(location, httpTexture);
                if (!decoded.get()) {
                    throw new java.io.IOException("Texture decoding failed");
                }
                File previousFile = REGISTERED_FILES.put(location, file);
                if (previousFile != null && !previousFile.equals(file)) {
                    try {
                        java.nio.file.Files.deleteIfExists(previousFile.toPath());
                    } catch (java.io.IOException e) {
                        LOGGER.debug("[WSkinLoader] Cannot delete superseded texture {}", previousFile, e);
                    }
                }
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    SkinCache.cacheModel(uuid, model != null ? model : detectModelFromFile(file));
                    SkinCache.cacheSkin(uuid, location);
                    if (source != null) {
                        PlayerSkinSourceCache.setSource(uuid, source);
                    }
                } else {
                    SkinCache.cacheCape(uuid, location);
                }
                result.complete(null);
            } catch (Exception e) {
                LOGGER.warn("[WSkinLoader] 纹理加载失败 uuid={} type={} url={}", uuid, type, texture.getUrl(), e);
                result.completeExceptionally(e);
            }
        }));
        return result;
    }

    /**
     * 从下载完成的皮肤文件推断模型类型（宽/窄）。
     *
     * <p>64x64 皮肤里，窄臂（slim）模型的
     * 第一层臂底面止于 x=49，因此 (50,16) 为全透明。
     */
    private static String detectModelFromFile(File file) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                return "default";
            }
            int alpha = image.getRGB(50, 16) >>> 24;
            return alpha == 0 ? "slim" : "default";
        } catch (Exception e) {
            LOGGER.debug("[WSkinLoader] 皮肤模型类型检测失败", e);
            return "default";
        }
    }

    private static File locateSkinsDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getSkinManager() == null) {
            return null;
        }
        if (minecraft.getSkinManager() instanceof com.wupeng.wskinloader.client.mixin.PlayerSkinProviderAccessor) {
            return ((com.wupeng.wskinloader.client.mixin.PlayerSkinProviderAccessor) minecraft.getSkinManager()).getSkinsDirectory();
        }
        return null;
    }
}
