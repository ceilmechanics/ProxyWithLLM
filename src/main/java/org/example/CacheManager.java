package org.example;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.concurrent.TimeUnit;

public class CacheManager {
    private static Cache<String, String> cache;

    static {
        cache = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(100)
                .build();
    }

    private CacheManager() {
    }

    public static Cache<String, String> getCache() {
        return cache;
    }
}