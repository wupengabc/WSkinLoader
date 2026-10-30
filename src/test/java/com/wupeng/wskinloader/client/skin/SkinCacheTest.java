package com.wupeng.wskinloader.client.skin;

import java.util.Set;
import java.util.UUID;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkinCacheTest {
    private static final ClientAsset.Texture TEXTURE = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath("wskinloader", "test-skin"));

    @AfterEach
    void clearCache() {
        SkinCache.clear();
    }

    @Test
    void cachedPlayersIncludesModelAndCapeEntries() {
        UUID skinOnly = UUID.randomUUID();
        UUID modelOnly = UUID.randomUUID();
        UUID capeOnly = UUID.randomUUID();

        SkinCache.cacheSkin(skinOnly, TEXTURE);
        SkinCache.cacheModel(modelOnly, PlayerModelType.SLIM);
        SkinCache.cacheCape(capeOnly, TEXTURE);

        Set<UUID> cachedPlayers = SkinCache.getCachedPlayers();

        assertTrue(cachedPlayers.contains(skinOnly));
        assertTrue(cachedPlayers.contains(modelOnly));
        assertTrue(cachedPlayers.contains(capeOnly));
    }

    @Test
    void cachedPlayersIsASnapshotAcrossCacheChanges() {
        UUID removed = UUID.randomUUID();
        UUID added = UUID.randomUUID();
        SkinCache.cacheSkin(removed, TEXTURE);
        Set<UUID> snapshot = SkinCache.getCachedPlayers();

        SkinCache.removePlayer(removed);
        SkinCache.cacheModel(added, PlayerModelType.SLIM);

        assertTrue(snapshot.contains(removed));
        assertFalse(snapshot.contains(added));
        assertFalse(SkinCache.getCachedPlayers().contains(removed));
        assertTrue(SkinCache.getCachedPlayers().contains(added));
    }

    @Test
    void removeSkinAlsoInvalidatesModelButPreservesCape() {
        UUID player = UUID.randomUUID();
        SkinCache.cacheSkin(player, TEXTURE);
        SkinCache.cacheCape(player, TEXTURE);
        SkinCache.cacheModel(player, PlayerModelType.WIDE);

        SkinCache.removeSkin(player);

        assertFalse(SkinCache.hasSkin(player));
        assertNull(SkinCache.getModel(player));
        assertTrue(SkinCache.hasCape(player));
    }

    @Test
    void removePlayerAndClearInvalidateModels() {
        UUID removed = UUID.randomUUID();
        UUID cleared = UUID.randomUUID();
        SkinCache.cacheSkin(removed, TEXTURE);
        SkinCache.cacheCape(removed, TEXTURE);
        SkinCache.cacheModel(removed, PlayerModelType.SLIM);
        SkinCache.cacheSkin(cleared, TEXTURE);
        SkinCache.cacheCape(cleared, TEXTURE);
        SkinCache.cacheModel(cleared, PlayerModelType.WIDE);

        SkinCache.removePlayer(removed);
        assertFalse(SkinCache.hasSkin(removed));
        assertFalse(SkinCache.hasCape(removed));
        assertNull(SkinCache.getModel(removed));

        SkinCache.clear();
        assertFalse(SkinCache.hasSkin(cleared));
        assertFalse(SkinCache.hasCape(cleared));
        assertNull(SkinCache.getModel(cleared));
        assertTrue(SkinCache.getCachedPlayers().isEmpty());
    }
}
