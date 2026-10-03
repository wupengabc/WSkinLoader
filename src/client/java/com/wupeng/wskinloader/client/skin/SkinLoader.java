package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.wupeng.wskinloader.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 统一的皮肤加载工具类（1.16.5 版）。
 *
 * <p>1.16.5 的 {@code SkinManager} 是同步注册模型：调用
 * {@code registerTexture(MinecraftProfileTexture, Type)} 会创建 {@code HttpTexture}
 * 并下载，最终返回纹理 {@link ResourceLocation}。因此这里在后台线程解析 URL，
 * 再回到客户端线程注册纹理并把结果写入 {@link SkinCache}。
 */
public class SkinLoader {

    private static final org.apache.logging.log4j.Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("wskinloader");

    private static final Set<UUID> LOADING = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> FAILED = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> INVALIDATION = new ConcurrentHashMap<>();
    private static final long FAILURE_COOLDOWN_MS = 30000L;
    private static final ExecutorService NETWORK_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "WSkinLoader-Network");
        thread.setDaemon(true);
        return thread;
    });

    public static void loadSkinForProfile(GameProfile profile) {
        if (profile == null || profile.getId() == null || profile.getName() == null) {
            return;
        }

        UUID uuid = profile.getId();
        String playerName = profile.getName();

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

        String skinName = resolveSourceName(override, true, playerName);
        String capeName = resolveSourceName(override, false, playerName);

        try {
            CompletableFuture.runAsync(() -> {
                try {
                    CompletableFuture<Void> loadingFuture =
                            loadResolved(uuid, skinName, capeName, override);

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
        SkinCache.removePlayer(uuid);
        PlayerSkinSourceCache.remove(uuid);
        PlayerNameCache.remove(uuid);
        FAILED.remove(uuid);
        LOADING.remove(uuid);
        INVALIDATION.put(uuid, System.currentTimeMillis());
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

    /** Nonce mixed into texture hashes; changes when a cache entry is dropped. */
    private static final UUID GLOBAL_INVALIDATION_KEY = new UUID(0L, 0L);

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

    private static String invalidationNonce(UUID uuid) {
        Long perPlayer = INVALIDATION.get(uuid);
        Long global = INVALIDATION.get(GLOBAL_INVALIDATION_KEY);
        long value = Math.max(perPlayer != null ? perPlayer : 0L, global != null ? global : 0L);
        return Long.toString(value);
    }

    private static CompletableFuture<Void> loadResolved(UUID cacheUuid, String skinName, String capeName,
                                                       ModConfig.PlayerOverride override) {
        boolean skipPremium = override != null && override.skipPremiumCheck;
        ModConfig config = ModConfig.getInstance();
        boolean premiumFirst = config.premiumFirst;
        boolean keepPremium = config.keepPremiumWhenUnavailable;

        CompletableFuture<Void> skinFuture = thisTextureFuture(cacheUuid, skinName,
                override != null && override.skin.useCustomApi,
                override == null ? -1 : override.skin.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.SKIN);

        CompletableFuture<Void> capeFuture = thisTextureFuture(cacheUuid, capeName,
                override != null && override.cape.useCustomApi,
                override == null ? -1 : override.cape.apiIndex,
                skipPremium, premiumFirst, keepPremium, MinecraftProfileTexture.Type.CAPE);

        return CompletableFuture.allOf(skinFuture, capeFuture);
    }

    private static CompletableFuture<Void> thisTextureFuture(UUID cacheUuid, String sourceName,
                                                             boolean useCustomApi, int apiIndex,
                                                             boolean skipPremium,
                                                             boolean premiumFirst,
                                                             boolean keepPremiumWhenUnavailable,
                                                             MinecraftProfileTexture.Type type) {
        if (useCustomApi || skipPremium || !premiumFirst) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex);
        }
        return loadPremiumTextureByName(cacheUuid, sourceName, type, apiIndex, keepPremiumWhenUnavailable);
    }

    private static CompletableFuture<Void> loadPremiumTextureByName(UUID cacheUuid, String sourceName,
                                                                    MinecraftProfileTexture.Type type,
                                                                    int apiIndex,
                                                                    boolean keepPremiumWhenUnavailable) {
        MojangApiChecker.PlayerProfile profile = MojangApiChecker.getPlayerProfile(sourceName);
        if (profile == null || !profile.isPremium || profile.uuid == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex);
        }

        MojangSessionApi.ProfileTextures textures = MojangSessionApi.getProfileTextures(profile.uuid);
        if (textures == null) {
            if (keepPremiumWhenUnavailable) {
                return CompletableFuture.completedFuture(null);
            }
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex);
        }

        String url = type == MinecraftProfileTexture.Type.SKIN ? textures.skinUrl : textures.capeUrl;
        if (url == null) {
            return loadCustomTexture(cacheUuid, sourceName, type, apiIndex);
        }

        if (type == MinecraftProfileTexture.Type.SKIN) {
            SkinCache.cacheModel(cacheUuid, textures.isSlim ? "slim" : "default");
        }
        return loadPremiumTexture(cacheUuid, profile.uuid, url, type);
    }

    private static CompletableFuture<Void> loadPremiumTexture(UUID cacheUuid, UUID mojangUuid, String url,
                                                              MinecraftProfileTexture.Type type) {
        String hash = sha1Hex(
                mojangUuid.toString() + "_premium_" + type.name() + "_" + url + "_" + invalidationNonce(cacheUuid));
        MinecraftProfileTexture texture = withHash(new MinecraftProfileTexture(url, null), hash);
        return register(cacheUuid, texture, type, "正版");
    }

    private static CompletableFuture<Void> loadCustomTexture(UUID uuid, String playerName,
                                                            MinecraftProfileTexture.Type type, int apiIndex) {
        ModConfig config = ModConfig.getInstance();
        String apiFingerprint = (type == MinecraftProfileTexture.Type.SKIN ? config.skinApis : config.capeApis).stream()
                .map(api -> api.url == null ? "" : api.url)
                .collect(java.util.stream.Collectors.joining("\u0000"));
        final String hash = sha1Hex(
                uuid + "_" + playerName + "_" + type.name() + "_" + apiIndex + "_" + apiFingerprint + "_" + invalidationNonce(uuid));

        CompletableFuture<MinecraftProfileTexture> future = apiIndex >= 0
                ? CustomSkinLoader.loadCustomSkinFromIndex(uuid, playerName, type, apiIndex)
                : CustomSkinLoader.loadCustomSkin(uuid, playerName, type);

        return future.thenCompose(texture -> {
            if (texture == null) {
                return CompletableFuture.completedFuture(null);
            }
            return register(uuid, withHash(texture, hash), type, null);
        });
    }

    /**
     * Registers a texture through vanilla's {@code SkinManager} on the client
     * thread and caches the resulting {@link ResourceLocation}.
     */
    private static CompletableFuture<Void> register(UUID uuid, MinecraftProfileTexture texture,
                                                    MinecraftProfileTexture.Type type, String source) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            result.complete(null);
            return result;
        }
        minecraft.execute(() -> {
            try {
                if (minecraft.getSkinManager() == null) {
                    result.complete(null);
                    return;
                }
                ResourceLocation location = minecraft.getSkinManager().registerTexture(texture, type);
                if (location != null) {
                    if (type == MinecraftProfileTexture.Type.SKIN) {
                        SkinCache.cacheSkin(uuid, location);
                        if (source != null) {
                            PlayerSkinSourceCache.setSource(uuid, source);
                        }
                        String detected = detectModelFromCacheFile(texture.getHash());
                        if (detected != null) {
                            SkinCache.cacheModel(uuid, detected);
                        }
                    } else {
                        SkinCache.cacheCape(uuid, location);
                    }
                }
                result.complete(null);
            } catch (Exception e) {
                LOGGER.error("[WSkinLoader] 注册纹理失败 uuid={} type={}", uuid, type, e);
                result.complete(null);
            }
        });
        return result;
    }

    private static MinecraftProfileTexture withHash(MinecraftProfileTexture texture, String hash) {
        try {
            java.lang.reflect.Field field = MinecraftProfileTexture.class.getDeclaredField("hash");
            field.setAccessible(true);
            field.set(texture, hash);
        } catch (Exception e) {
            LOGGER.warn("[WSkinLoader] 无法设置纹理 hash，将使用原版缓存文件名", e);
        }
        return texture;
    }

    /**
     * 从下载完成的皮肤文件推断模型类型（宽/窄）。
     *
     * <p>自定义 API 不提供模型元数据，只能按皮肤像素判断：64x64 皮肤里，
     * 窄臂（slim）模型的第一层臂底面止于 x=49，因此 (50,16) 为全透明。
     */
    private static String detectModelFromCacheFile(String textureHash) {
        try {
            if (textureHash == null) {
                return null;
            }
            String fileName = sha1Hex(textureHash);
            File dir = locateSkinsDirectory();
            if (dir == null) {
                return null;
            }
            File parent = new File(dir, fileName.length() > 2 ? fileName.substring(0, 2) : "xx");
            File file = new File(parent, fileName);
            if (!file.isFile()) {
                return null;
            }
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(file);
            if (image == null || image.getWidth() != 64 || image.getHeight() != 64) {
                return "default";
            }
            int alpha = image.getRGB(50, 16) >>> 24;
            return alpha == 0 ? "slim" : "default";
        } catch (Exception e) {
            LOGGER.debug("[WSkinLoader] 皮肤模型类型检测失败", e);
            return null;
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
