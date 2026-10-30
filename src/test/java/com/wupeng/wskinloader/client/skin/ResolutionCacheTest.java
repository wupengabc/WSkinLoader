package com.wupeng.wskinloader.client.skin;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionCacheTest {
    @Test
    void missingAndFailedResultsExpireWithoutInvalidatingSuccessfulTextures() {
        AtomicLong clock = new AtomicLong(100);
        ResolutionCache cache = new ResolutionCache(4, clock::get);
        UUID missing = UUID.randomUUID(), failed = UUID.randomUUID(), complete = UUID.randomUUID();
        cache.remember(missing, 300_000);
        cache.remember(failed, 30_000);
        cache.remember(complete, Long.MAX_VALUE);
        assertTrue(cache.blocksLoad(missing));
        assertTrue(cache.blocksLoad(failed));
        clock.addAndGet(30_000);
        assertFalse(cache.blocksLoad(failed));
        assertTrue(cache.blocksLoad(missing));
        clock.addAndGet(270_000);
        assertFalse(cache.blocksLoad(missing));
        assertTrue(cache.blocksLoad(complete));
        cache.remove(complete);
        assertFalse(cache.blocksLoad(complete));
    }

    @Test
    void deadlinesAreBoundedAndExplicitRefreshClearsThem() {
        ResolutionCache cache = new ResolutionCache(2, () -> 0);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID(), third = UUID.randomUUID();
        cache.remember(first, Long.MAX_VALUE);
        cache.remember(second, Long.MAX_VALUE);
        assertTrue(cache.blocksLoad(first));
        cache.remember(third, Long.MAX_VALUE);
        assertFalse(cache.blocksLoad(second));
        assertTrue(cache.blocksLoad(first));
        cache.clear();
        assertFalse(cache.blocksLoad(first));
        assertFalse(cache.blocksLoad(third));
    }
}
