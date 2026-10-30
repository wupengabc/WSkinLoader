package com.wupeng.wskinloader.client.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BoundedCache 单元测试
 */
public class BoundedCacheTest {

    @Test
    public void testCacheRespectsSizeLimit() {
        BoundedCache<Integer, String> cache = new BoundedCache<>(3);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        assertEquals(3, cache.currentSize());
        assertEquals(3, cache.maxSize());

        // 添加第四个元素应该移除最老的
        cache.put(4, "four");

        assertEquals(3, cache.currentSize());
        assertFalse(cache.containsKey(1)); // 最老的应该被移除
        assertTrue(cache.containsKey(2));
        assertTrue(cache.containsKey(3));
        assertTrue(cache.containsKey(4));
    }

    @Test
    public void testLRUEviction() {
        BoundedCache<Integer, String> cache = new BoundedCache<>(3);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // 访问 key 1，使其成为最近使用的
        cache.get(1);

        // 添加新元素，应该移除 key 2（最久未使用）
        cache.put(4, "four");

        assertTrue(cache.containsKey(1));
        assertFalse(cache.containsKey(2)); // 最久未使用，被移除
        assertTrue(cache.containsKey(3));
        assertTrue(cache.containsKey(4));
    }

    @Test
    public void testEmptyCache() {
        BoundedCache<String, Integer> cache = new BoundedCache<>(10);

        assertEquals(0, cache.currentSize());
        assertEquals(10, cache.maxSize());
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testCacheClear() {
        BoundedCache<Integer, String> cache = new BoundedCache<>(5);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        assertEquals(3, cache.currentSize());

        cache.clear();

        assertEquals(0, cache.currentSize());
        assertTrue(cache.isEmpty());
    }

    @Test
    public void testCacheWithSingleElement() {
        BoundedCache<String, String> cache = new BoundedCache<>(1);

        cache.put("key1", "value1");
        assertEquals(1, cache.currentSize());
        assertTrue(cache.containsKey("key1"));

        cache.put("key2", "value2");
        assertEquals(1, cache.currentSize());
        assertFalse(cache.containsKey("key1"));
        assertTrue(cache.containsKey("key2"));
    }

    @Test
    public void testCacheUpdate() {
        BoundedCache<Integer, String> cache = new BoundedCache<>(3);

        cache.put(1, "one");
        cache.put(2, "two");
        cache.put(3, "three");

        // 更新现有键不应该增加大小
        cache.put(2, "TWO");

        assertEquals(3, cache.currentSize());
        assertEquals("TWO", cache.get(2));
    }
}
