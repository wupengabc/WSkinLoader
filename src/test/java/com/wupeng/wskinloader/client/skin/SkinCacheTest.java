package com.wupeng.wskinloader.client.skin;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkinCacheTest {
    @After
    public void cleanup() {
        SkinCache.clear();
    }

    @Test
    public void vanillaSkinIsListedWithoutBlockingCustomLoading() {
        UUID uuid = UUID.randomUUID();
        ResourceLocation vanilla = new ResourceLocation("minecraft", "skins/test");
        SkinCache.cacheVanillaSkin(uuid, vanilla, "slim");
        assertTrue(SkinCache.getCachedPlayers().contains(uuid));
        assertEquals(vanilla, SkinCache.getCachedSkin(uuid));
        assertEquals("slim", SkinCache.getCachedModel(uuid));
        assertNull(SkinCache.getSkin(uuid));
        assertFalse(SkinCache.hasSkin(uuid));
    }

    @Test
    public void customSkinTakesPriorityAndPlayerIsNotCountedTwice() {
        UUID uuid = UUID.randomUUID();
        ResourceLocation vanilla = new ResourceLocation("minecraft", "skins/test");
        ResourceLocation custom = new ResourceLocation("wskinloader", "skins/test");
        SkinCache.cacheVanillaSkin(uuid, vanilla, "slim");
        SkinCache.cacheSkin(uuid, custom);
        SkinCache.cacheModel(uuid, "default");
        assertEquals(custom, SkinCache.getCachedSkin(uuid));
        assertEquals("default", SkinCache.getCachedModel(uuid));
        assertEquals(1, SkinCache.getCachedPlayers().size());
    }

    @Test
    public void clearingOnePlayerRemovesBothCacheSources() {
        UUID uuid = UUID.randomUUID();
        ResourceLocation vanilla = new ResourceLocation("minecraft", "skins/test");
        SkinCache.cacheVanillaSkin(uuid, vanilla, "slim");
        SkinCache.cacheVanillaCape(uuid, vanilla);
        SkinCache.removePlayer(uuid);
        assertNull(SkinCache.getCachedSkin(uuid));
        assertNull(SkinCache.getCachedCape(uuid));
        assertNull(SkinCache.getCachedModel(uuid));
        assertTrue(SkinCache.getCachedPlayers().isEmpty());
    }

    @Test
    public void clearingAllRemovesVanillaEntriesAndSnapshotIsIndependent() {
        UUID uuid = UUID.randomUUID();
        ResourceLocation vanilla = new ResourceLocation("minecraft", "skins/test");
        SkinCache.cacheVanillaSkin(uuid, vanilla, null);
        SkinCache.cacheVanillaCape(uuid, vanilla);
        SkinCache.getCachedPlayers().clear();
        assertEquals(vanilla, SkinCache.getCachedSkin(uuid));
        SkinCache.clear();
        assertNull(SkinCache.getCachedSkin(uuid));
        assertNull(SkinCache.getCachedCape(uuid));
        assertNull(SkinCache.getCachedModel(uuid));
        assertTrue(SkinCache.getCachedPlayers().isEmpty());
    }
}
