package com.wupeng.wskinloader.client.skin;

import com.wupeng.wskinloader.client.util.SkinTextureHelper;
import java.util.UUID;
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
}
