package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PremiumTextureResolverTest {
    private final UUID uuid = UUID.randomUUID();
    private MojangApiChecker.PlayerProfile premium() {
        return new MojangApiChecker.PlayerProfile(uuid, "Player", true);
    }

    @Test
    void unavailableProfileCannotFallbackWhenProtectionEnabled() {
        assertThrows(IOException.class, () -> PremiumTextureResolver.resolve("Player",
                MinecraftProfileTexture.Type.SKIN, true, name -> null,
                id -> { fail("Session lookup must not run"); return null; }));
    }

    @Test
    void unavailableSessionCannotFallbackWhenProtectionEnabled() {
        assertThrows(IOException.class, () -> PremiumTextureResolver.resolve("Player",
                MinecraftProfileTexture.Type.SKIN, true, name -> premium(), id -> null));
    }

    @Test
    void absentAccountAndAbsentTextureAllowFallback() throws IOException {
        assertNull(PremiumTextureResolver.resolve("Player", MinecraftProfileTexture.Type.SKIN, true,
                name -> new MojangApiChecker.PlayerProfile(null, name, false), id -> null));
        assertNull(PremiumTextureResolver.resolve("Player", MinecraftProfileTexture.Type.CAPE, true,
                name -> premium(), id -> new MojangSessionApi.ProfileTextures("skin", null, true)));
    }

    @Test
    void disabledProtectionAllowsUnavailableServicesToFallback() throws IOException {
        assertNull(PremiumTextureResolver.resolve("Player", MinecraftProfileTexture.Type.SKIN, false,
                name -> null, id -> null).url());
        assertNull(PremiumTextureResolver.resolve("Player", MinecraftProfileTexture.Type.SKIN, false,
                name -> premium(), id -> null).url());
    }

    @Test
    void premiumTextureRetainsSourceUuidAndModel() throws IOException {
        var result = PremiumTextureResolver.resolve("Player", MinecraftProfileTexture.Type.SKIN, true,
                name -> premium(), id -> new MojangSessionApi.ProfileTextures("skin", "cape", true));
        assertEquals(uuid, result.uuid());
        assertEquals("skin", result.url());
        assertTrue(result.slim());
    }
}
