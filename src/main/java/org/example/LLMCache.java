package org.example;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.handler.codec.http.FullHttpResponse;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class LLMCache {
    private static LLMCache _LLMCache;

    private static final Cache<Integer, String> cache = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private LLMCache() {}

    public static LLMCache getInstance() {
        if (_LLMCache == null) {
            _LLMCache = new LLMCache();
        }
        return _LLMCache;
    }

    public String getResponse(final JSONObject requestBody) {
        return cache.getIfPresent(generateHashCode(requestBody));
    }

    public void addResponseToCache(final JSONObject requestBody, String response) {
        cache.put(generateHashCode(requestBody), response);
    }

    private int generateHashCode(final JSONObject requestBody) {
        JSONObject copyJson = new JSONObject(requestBody.toString());
        copyJson.remove("session_id");
        copyJson.remove("temperature");
        return copyJson.toString().hashCode();
    }
}
