package com.amigoscode.ds;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LruCacheTest {

    @Test
    @Disabled("story 3: remove this line to begin")
    void evictsTheLeastRecentlyUsedKey() {
        // LeetCode 146 example
        LruCache<Integer, Integer> cache = new LruCache<>(2);
        cache.put(1, 1);
        cache.put(2, 2);
        assertThat(cache.get(1)).contains(1);
        cache.put(3, 3);                        // evicts 2
        assertThat(cache.get(2)).isEmpty();
        cache.put(4, 4);                        // evicts 1
        assertThat(cache.get(1)).isEmpty();
        assertThat(cache.get(3)).contains(3);
        assertThat(cache.get(4)).contains(4);
    }
}
