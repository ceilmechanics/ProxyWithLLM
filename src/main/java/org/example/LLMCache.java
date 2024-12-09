package org.example;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.codec.http.FullHttpResponse;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LLMCache {
    private static LLMCache LLMCache;

    // Loading cache with automatic value computation
    private static final Cache<Integer, String> cache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private LLMCache() {}

    public static LLMCache getInstance() {
        if (LLMCache == null) {
            LLMCache = new LLMCache();
            System.out.println("\n>>>>>>>>>>> first time calling cache");
        }
        return LLMCache;
    }

    public String getResponse(final JSONObject requestBody) {
        String response = cache.getIfPresent(generateHashCode(requestBody));
        System.out.println("\n" + generateHashCode(requestBody) + " found in cache: " + (response != null));
        System.out.println("Cache size: " + cache.estimatedSize() + "\n");
        return response;
    }

    public void addResponseToCache(final JSONObject requestBody, String response) {
        System.out.println("\nAdd to cache\n");
        System.out.println("hashKey " + generateHashCode(requestBody));
        cache.put(generateHashCode(requestBody), response);
    }

    private int generateHashCode(final JSONObject requestBody) {
        JSONObject copyJson = new JSONObject(requestBody.toString());
        copyJson.remove("session_id");
        copyJson.remove("temperature");
        return copyJson.toString().hashCode();
    }
}
