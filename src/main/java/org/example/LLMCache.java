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
    private final Cache<Integer, FullHttpResponse> cache;

    private LLMCache() {
        cache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats()   // Add this line
                .build();
    }

    public static LLMCache getInstance() {
        if (LLMCache == null) {
            LLMCache = new LLMCache();
            System.out.println(">>>>>>>>>>> first time calling cache");
        }
        return LLMCache;
    }

    public FullHttpResponse getResponse(final JSONObject requestBody) {
        FullHttpResponse response = cache.getIfPresent(generateHashCode(requestBody));
        System.out.println("Found in cache: " + (response != null));
        System.out.println("Cache size: " + cache.estimatedSize());

        return response;
    }

    public void addResponseToCache(final JSONObject requestBody, FullHttpResponse response) {
        cache.put(generateHashCode(requestBody), response);
    }

    private int generateHashCode(final JSONObject requestBody) {
        JSONObject copyJson = new JSONObject(requestBody.toString());
        copyJson.remove("session_id");
        copyJson.remove("temperature");
        return copyJson.toString().hashCode();
    }
}
