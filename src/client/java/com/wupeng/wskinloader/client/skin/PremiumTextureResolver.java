package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Function;

final class PremiumTextureResolver {
    record Texture(UUID uuid, String url, boolean slim) {}

    /** Null means authoritative absence; a null URL marks a temporary fallback. */
    static Texture resolve(String name, MinecraftProfileTexture.Type type, boolean keepPremium,
            Function<String, MojangApiChecker.PlayerProfile> profiles,
            Function<UUID, MojangSessionApi.ProfileTextures> textures) throws IOException {
        var profile = profiles.apply(name);
        if (profile == null) {
            if (keepPremium) throw new IOException("Mojang profile service unavailable");
            return new Texture(null, null, false);
        }
        if (!profile.isPremium || profile.uuid == null) return null;
        var result = textures.apply(profile.uuid);
        if (result == null) {
            if (keepPremium) throw new IOException("Mojang session service unavailable");
            return new Texture(null, null, false);
        }
        String url = type == MinecraftProfileTexture.Type.SKIN ? result.skinUrl : result.capeUrl;
        return url == null ? null : new Texture(profile.uuid, url, result.isSlim);
    }
}
