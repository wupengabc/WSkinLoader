package com.wupeng.wskinloader.client.skin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

public class SkinLoaderTest {
    @After
    public void cleanup() {
        SkinLoader.clearLoadingState();
        SkinCache.clear();
        PlayerNameCache.clear();
        PlayerSkinSourceCache.clear();
    }

    @Test
    public void invalidatingTexturesPreservesPlayerIdentity() throws Exception {
        UUID uuid = UUID.randomUUID();
        PlayerNameCache.cacheName(uuid, "Player");
        PlayerSkinSourceCache.setSource(uuid, "API");
        SkinCache.cacheModel(uuid, "slim");
        loading().put(uuid, 1L);

        SkinLoader.invalidate(uuid);

        assertEquals("Player", PlayerNameCache.getName(uuid));
        assertNull(PlayerSkinSourceCache.getSource(uuid));
        assertNull(SkinCache.getModel(uuid));
        assertFalse(isCurrent(uuid, 1L));
    }

    @Test
    public void staleCompletionCannotRemoveNewRequestOrRecordFailure() throws Exception {
        UUID uuid = UUID.randomUUID();
        loading().put(uuid, 2L);

        Method finish = SkinLoader.class.getDeclaredMethod("finishLoading", UUID.class, long.class, Throwable.class);
        finish.setAccessible(true);
        finish.invoke(null, uuid, 1L, new RuntimeException("Old request failed"));

        assertTrue(isCurrent(uuid, 2L));
        assertFalse(isCurrent(uuid, 1L));
        Field failed = SkinLoader.class.getDeclaredField("FAILED");
        failed.setAccessible(true);
        assertFalse(((Map<?, ?>) failed.get(null)).containsKey(uuid));
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Long> loading() throws Exception {
        Field field = SkinLoader.class.getDeclaredField("LOADING");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(null);
    }

    private boolean isCurrent(UUID uuid, long request) throws Exception {
        Method method = SkinLoader.class.getDeclaredMethod("isCurrent", UUID.class, long.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, uuid, request);
    }
}
