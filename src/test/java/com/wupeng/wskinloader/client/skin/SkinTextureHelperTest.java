package com.wupeng.wskinloader.client.skin;

import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import java.util.UUID;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkinTextureHelperTest {
    private final UUID player = UUID.randomUUID();
    private final ResourceLocation vanilla = new ResourceLocation("minecraft", "capes/local");
    private final ResourceLocation custom = new ResourceLocation("wskinloader", "capes/local");
    private final ResourceLocation target = new ResourceLocation("wskinloader", "capes/target");

    @After
    public void cleanup() {
        SkinTextureHelper.setPreviewCape(null);
        SkinCache.clear();
    }

    @Test
    public void normalRenderingKeepsVanillaCapeWhenNoCustomCapeExists() {
        assertEquals(vanilla, SkinTextureHelper.resolveCapeTexture(player, vanilla));
        assertNull(SkinTextureHelper.resolveCapeTexture(player, null));
    }

    @Test
    public void normalRenderingPrefersCustomCape() {
        SkinCache.cacheCape(player, custom);
        assertEquals(custom, SkinTextureHelper.resolveCapeTexture(player, vanilla));
    }

    @Test
    public void capelessPreviewSuppressesBothLocalCapeSourcesWithoutChangingCaches() {
        SkinCache.cacheCape(player, custom);
        SkinCache.cacheVanillaCape(player, vanilla);
        SkinTextureHelper.setPreviewCape(new SkinTextureHelper.PreviewCape(player, null));
        assertNull(SkinTextureHelper.resolveCapeTexture(player, vanilla));
        assertEquals(custom, SkinCache.getCape(player));
        assertEquals(custom, SkinCache.getCachedCape(player));
        SkinCache.removeCape(player);
        assertNull(SkinTextureHelper.resolveCapeTexture(player, vanilla));
        assertEquals(vanilla, SkinCache.getCachedCape(player));
    }

    @Test
    public void previewUsesTargetCapeInsteadOfLocalCape() {
        SkinCache.cacheCape(player, custom);
        SkinTextureHelper.setPreviewCape(new SkinTextureHelper.PreviewCape(player, target));
        assertEquals(target, SkinTextureHelper.resolveCapeTexture(player, vanilla));
        assertEquals(custom, SkinCache.getCape(player));
    }

    @Test
    public void previewDoesNotOverrideOtherPlayers() {
        SkinTextureHelper.setPreviewCape(new SkinTextureHelper.PreviewCape(player, null));
        assertEquals(vanilla, SkinTextureHelper.resolveCapeTexture(UUID.randomUUID(), vanilla));
    }

    @Test
    public void failedPreviewRestoresPreviousOverrideAndNormalRendering() {
        SkinCache.cacheCape(player, custom);
        SkinTextureHelper.PreviewCape previous = SkinTextureHelper.setPreviewCape(
                new SkinTextureHelper.PreviewCape(player, target));
        try {
            SkinTextureHelper.PreviewCape outer = SkinTextureHelper.setPreviewCape(
                    new SkinTextureHelper.PreviewCape(player, null));
            try {
                assertNull(SkinTextureHelper.resolveCapeTexture(player, vanilla));
                throw new IllegalStateException("render failed");
            } finally {
                SkinTextureHelper.setPreviewCape(outer);
            }
        } catch (IllegalStateException expected) {
            assertEquals(target, SkinTextureHelper.resolveCapeTexture(player, vanilla));
        } finally {
            SkinTextureHelper.setPreviewCape(previous);
        }
        assertEquals(custom, SkinTextureHelper.resolveCapeTexture(player, vanilla));
    }

    @Test
    public void unchangedAggregateRetainsOriginalInstanceAndVanillaModel() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.SLIM, true);
        assertSame(original, SkinTextureHelper.resolvePlayerSkin(player, original));
        assertSame(original, SkinTextureHelper.resolvePlayerSkin(null, original));
    }

    @Test
    public void aggregateCustomSkinPreservesVanillaMetadataAndCapeFallback() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.SLIM, false);
        SkinCache.cacheSkin(player, custom);

        PlayerSkin resolved = SkinTextureHelper.resolvePlayerSkin(player, original);
        assertEquals(custom, resolved.texture());
        assertEquals(original.textureUrl(), resolved.textureUrl());
        assertEquals(vanilla, resolved.capeTexture());
        assertEquals(original.elytraTexture(), resolved.elytraTexture());
        assertEquals(PlayerSkin.Model.SLIM, resolved.model());
        assertFalse(resolved.secure());
        assertEquals(new ResourceLocation("minecraft", "skins/local"), original.texture());
        assertNull(SkinCache.getCachedModel(player));
    }

    @Test
    public void aggregateCustomCapeDoesNotReplaceSkinOrElytra() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.WIDE, true);
        SkinCache.cacheCape(player, custom);

        PlayerSkin resolved = SkinTextureHelper.resolvePlayerSkin(player, original);
        assertEquals(original.texture(), resolved.texture());
        assertEquals(custom, resolved.capeTexture());
        assertEquals(original.elytraTexture(), resolved.elytraTexture());
        assertEquals(original.textureUrl(), resolved.textureUrl());
        assertTrue(resolved.secure());
    }

    @Test
    public void aggregateDoesNotApplyVanillaObservationCachesAsCustomOverrides() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.WIDE, true);
        SkinCache.cacheVanillaSkin(player, custom, "slim");
        SkinCache.cacheVanillaCape(player, target);

        assertSame(original, SkinTextureHelper.resolvePlayerSkin(player, original));
        assertNull(SkinCache.getSkin(player));
        assertNull(SkinCache.getCape(player));
        assertEquals(custom, SkinCache.getCachedSkin(player));
        assertEquals(target, SkinCache.getCachedCape(player));
    }

    @Test
    public void aggregateResolvesCachedSlimAndDefaultModels() {
        PlayerSkin wide = playerSkin(PlayerSkin.Model.WIDE, true);
        SkinCache.cacheModel(player, "slim");
        PlayerSkin slim = SkinTextureHelper.resolvePlayerSkin(player, wide);
        assertEquals(PlayerSkin.Model.SLIM, slim.model());
        assertEquals(wide.texture(), slim.texture());
        assertEquals(wide.capeTexture(), slim.capeTexture());

        SkinCache.cacheModel(player, "default");
        assertEquals(PlayerSkin.Model.WIDE, SkinTextureHelper.resolvePlayerSkin(player, slim).model());
    }

    @Test
    public void aggregateCapelessPreviewSuppressesBothCapeSourcesWithoutMutatingThem() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.WIDE, true);
        SkinCache.cacheCape(player, custom);
        SkinCache.cacheVanillaCape(player, vanilla);
        SkinTextureHelper.PreviewCape previous = SkinTextureHelper.setPreviewCape(
                new SkinTextureHelper.PreviewCape(player, null));
        try {
            PlayerSkin resolved = SkinTextureHelper.resolvePlayerSkin(player, original);
            assertNull(resolved.capeTexture());
            assertEquals(original.elytraTexture(), resolved.elytraTexture());
            assertEquals(vanilla, original.capeTexture());
            assertEquals(custom, SkinCache.getCape(player));
            assertEquals(custom, SkinCache.getCachedCape(player));
            assertSame(original, SkinTextureHelper.resolvePlayerSkin(UUID.randomUUID(), original));
        } finally {
            SkinTextureHelper.setPreviewCape(previous);
        }
        assertEquals(custom, SkinTextureHelper.resolvePlayerSkin(player, original).capeTexture());
    }

    @Test
    public void aggregatePreviewUsesTargetCapeAndRestoresOuterPreview() {
        PlayerSkin original = playerSkin(PlayerSkin.Model.SLIM, false);
        SkinTextureHelper.setPreviewCape(new SkinTextureHelper.PreviewCape(player, target));
        SkinTextureHelper.PreviewCape outer = SkinTextureHelper.setPreviewCape(
                new SkinTextureHelper.PreviewCape(player, null));
        try {
            assertNull(SkinTextureHelper.resolvePlayerSkin(player, original).capeTexture());
        } finally {
            SkinTextureHelper.setPreviewCape(outer);
        }
        assertEquals(target, SkinTextureHelper.resolvePlayerSkin(player, original).capeTexture());
        assertNull(SkinCache.getCape(player));
    }

    @Test
    public void aggregateCustomSkinPreservesNullVanillaUrlAndCape() {
        PlayerSkin original = new PlayerSkin(new ResourceLocation("minecraft", "skins/default"),
                null, null, null, PlayerSkin.Model.WIDE, true);
        SkinCache.cacheSkin(player, custom);
        PlayerSkin resolved = SkinTextureHelper.resolvePlayerSkin(player, original);

        assertEquals(custom, resolved.texture());
        assertNull(resolved.textureUrl());
        assertNull(resolved.capeTexture());
        assertNull(resolved.elytraTexture());
        assertTrue(resolved.secure());
    }

    private PlayerSkin playerSkin(PlayerSkin.Model model, boolean secure) {
        return new PlayerSkin(new ResourceLocation("minecraft", "skins/local"),
                "https://textures.minecraft.net/texture/local", vanilla,
                new ResourceLocation("minecraft", "elytra/local"), model, secure);
    }
}
