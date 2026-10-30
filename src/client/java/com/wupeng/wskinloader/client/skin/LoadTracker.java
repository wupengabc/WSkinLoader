package com.wupeng.wskinloader.client.skin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Serializes invalidation and publication without holding a lock during I/O. */
final class LoadTracker {
    private final Map<UUID, Long> active = new HashMap<>();
    private long sequence;

    synchronized long begin(UUID uuid) {
        if (active.containsKey(uuid)) return -1;
        long token = ++sequence;
        active.put(uuid, token);
        return token;
    }

    synchronized boolean isCurrent(UUID uuid, long token) {
        return Long.valueOf(token).equals(active.get(uuid));
    }

    synchronized boolean publish(UUID uuid, long token, Runnable action) {
        if (!isCurrent(uuid, token)) return false;
        action.run();
        return true;
    }

    synchronized void finish(UUID uuid, long token, Runnable action) {
        if (publish(uuid, token, action)) active.remove(uuid);
    }

    synchronized void invalidate(UUID uuid, Runnable action) {
        active.remove(uuid);
        action.run();
    }

    synchronized void invalidateAll(Runnable action) {
        active.clear();
        action.run();
    }
}
