package com.wupeng.wskinloader.client.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 有界的 LRU 缓存实现
 * 当缓存达到最大容量时，自动移除最久未使用的条目
 *
 * @param <K> 键类型
 * @param <V> 值类型
 */
public class BoundedCache<K, V> extends LinkedHashMap<K, V> {
    private final int maxSize;

    /**
     * 创建一个有界缓存
     *
     * @param maxSize 最大容量
     */
    public BoundedCache(int maxSize) {
        super(16, 0.75f, true); // accessOrder = true 启用 LRU
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxSize;
    }

    /**
     * 获取当前缓存大小
     */
    public int currentSize() {
        return size();
    }

    /**
     * 获取最大容量
     */
    public int maxSize() {
        return maxSize;
    }
}
