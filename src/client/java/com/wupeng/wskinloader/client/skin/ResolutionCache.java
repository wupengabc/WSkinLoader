package com.wupeng.wskinloader.client.skin;

import com.wupeng.wskinloader.client.util.BoundedCache;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Retry deadlines are independent of the lifetime of successfully loaded textures. */
final class ResolutionCache {
    private final Map<UUID, Long> deadlines;
    private final LongSupplier clock;

    ResolutionCache(int capacity, LongSupplier clock) {
        this.deadlines = new BoundedCache<>(capacity);
        this.clock = clock;
    }

    synchronized boolean blocksLoad(UUID uuid) {
        Long deadline = deadlines.get(uuid);
        if (deadline == null) return false;
        if (deadline == Long.MAX_VALUE || clock.getAsLong() < deadline) return true;
        deadlines.remove(uuid);
        return false;
    }

    synchronized void remember(UUID uuid, long delayMillis) {
        deadlines.put(uuid, delayMillis == Long.MAX_VALUE ? Long.MAX_VALUE : clock.getAsLong() + delayMillis);
    }

    synchronized void remove(UUID uuid) {
        deadlines.remove(uuid);
    }

    synchronized void clear() {
        deadlines.clear();
    }
}
