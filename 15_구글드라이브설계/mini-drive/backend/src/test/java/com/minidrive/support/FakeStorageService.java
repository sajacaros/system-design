package com.minidrive.support;

import com.minidrive.storage.StorageService;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory StorageService for unit/web-slice tests (no MinIO required).
 */
public class FakeStorageService implements StorageService {

    private final Map<String, byte[]> store = new HashMap<>();

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        try {
            store.put(key, content.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void copy(String sourceKey, String destKey) {
        byte[] data = store.get(sourceKey);
        if (data != null) {
            store.put(destKey, data);
        }
    }

    @Override
    public InputStream get(String key) {
        return new ByteArrayInputStream(store.getOrDefault(key, new byte[0]));
    }

    @Override
    public String presignGet(String key, Duration ttl, String responseContentType) {
        String url = "http://fake-storage/" + key + "?ttl=" + ttl.getSeconds();
        if (responseContentType != null && !responseContentType.isBlank()) {
            url += "&response-content-type=" + java.net.URLEncoder.encode(
                    responseContentType, java.nio.charset.StandardCharsets.UTF_8);
        }
        return url;
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public void deletePrefix(String prefix) {
        store.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public boolean exists(String key) {
        return store.containsKey(key);
    }
}
