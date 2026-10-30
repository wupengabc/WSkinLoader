package com.wupeng.wskinloader.client.skin;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class LoadTrackerTest {
    @Test
    void staleCompletionCannotPublishOrFinishNewLoad() {
        LoadTracker tracker = new LoadTracker();
        UUID uuid = UUID.randomUUID();
        long old = tracker.begin(uuid);
        assertEquals(-1, tracker.begin(uuid));
        tracker.invalidate(uuid, () -> {});
        long current = tracker.begin(uuid);
        AtomicInteger writes = new AtomicInteger();
        assertFalse(tracker.publish(uuid, old, writes::incrementAndGet));
        tracker.finish(uuid, old, writes::incrementAndGet);
        assertTrue(tracker.isCurrent(uuid, current));
        tracker.finish(uuid, current, writes::incrementAndGet);
        assertEquals(1, writes.get());
        assertFalse(tracker.isCurrent(uuid, current));
    }

    @Test
    void globalInvalidationRejectsAllOldResults() {
        LoadTracker tracker = new LoadTracker();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        long a = tracker.begin(first);
        long b = tracker.begin(second);
        tracker.invalidateAll(() -> {});
        assertFalse(tracker.isCurrent(first, a));
        assertFalse(tracker.isCurrent(second, b));
        assertTrue(tracker.begin(first) > b);
    }

    @Test
    void invalidationAndPublicationAreSerialized() throws Exception {
        LoadTracker tracker = new LoadTracker();
        UUID uuid = UUID.randomUUID();
        long token = tracker.begin(uuid);
        AtomicInteger cache = new AtomicInteger();
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        var publish = CompletableFuture.runAsync(() -> tracker.publish(uuid, token, () -> {
            publishing.countDown();
            try {
                if (!proceed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Publication timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            cache.set(1);
        }));
        assertTrue(publishing.await(5, TimeUnit.SECONDS));
        var invalidate = CompletableFuture.runAsync(() -> tracker.invalidate(uuid, () -> cache.set(0)));
        proceed.countDown();
        CompletableFuture.allOf(publish, invalidate).get(5, TimeUnit.SECONDS);
        assertEquals(0, cache.get());
        assertFalse(tracker.publish(uuid, token, () -> cache.set(2)));
    }
}
