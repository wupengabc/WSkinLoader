package com.wupeng.wskinloader.client.skin;

import com.mojang.authlib.GameProfile;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class PlayerNameCacheTest {
    @After
    public void cleanup() {
        PlayerNameCache.clear();
    }

    @Test
    public void profileCachesItsOwnPlayerName() {
        UUID uuid = UUID.randomUUID();
        PlayerNameCache.cache(new GameProfile(uuid, "Player"));
        assertEquals("Player", PlayerNameCache.getName(uuid));
    }

    @Test
    public void missingOrBlankNamesCannotOverwriteKnownName() {
        UUID uuid = UUID.randomUUID();
        PlayerNameCache.cacheName(uuid, " Player ");
        PlayerNameCache.cacheName(uuid, null);
        PlayerNameCache.cacheName(uuid, "");
        PlayerNameCache.cacheName(uuid, "  ");
        PlayerNameCache.cache(null);
        PlayerNameCache.cache(new GameProfile(uuid, ""));
        PlayerNameCache.cache(new GameProfile(uuid, "  "));
        PlayerNameCache.cacheName(null, "Other");
        assertEquals("Player", PlayerNameCache.getName(uuid));
    }

    @Test
    public void snapshotCannotMutateCache() {
        UUID uuid = UUID.randomUUID();
        PlayerNameCache.cacheName(uuid, "Player");
        Map<UUID, String> snapshot = PlayerNameCache.snapshot();
        snapshot.clear();
        assertEquals("Player", PlayerNameCache.getName(uuid));
    }

    @Test
    public void explicitRemoveAndClearStillWork() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        PlayerNameCache.cacheName(first, "First");
        PlayerNameCache.cacheName(second, "Second");
        PlayerNameCache.remove(first);
        assertNull(PlayerNameCache.getName(first));
        assertEquals("Second", PlayerNameCache.getName(second));
        PlayerNameCache.clear();
        assertTrue(PlayerNameCache.snapshot().isEmpty());
    }
}
