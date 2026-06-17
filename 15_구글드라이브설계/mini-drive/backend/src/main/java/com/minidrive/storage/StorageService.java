package com.minidrive.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * Storage abstraction (storage-layout.md). Backed by AWS SDK v2 S3Client against
 * MinIO with path-style access; swappable to AWS S3 by changing endpoint/credentials.
 */
public interface StorageService {

    /** Store an object. */
    void put(String key, InputStream content, long contentLength, String contentType);

    /** Copy an existing object to a new key (used for versioning/restore). */
    void copy(String sourceKey, String destKey);

    /** Open an object's content for reading. */
    InputStream get(String key);

    /** Generate a short-lived presigned GET URL for download. */
    default String presignGet(String key, Duration ttl) {
        return presignGet(key, ttl, null);
    }

    /**
     * Generate a short-lived presigned GET URL whose response forces the given Content-Type.
     * When {@code responseContentType} is non-null it is bound to the request so the served
     * download carries that header (e.g. {@code text/plain; charset=UTF-8}); this also fixes
     * objects already stored with a charset-less Content-Type. Null = no override.
     */
    String presignGet(String key, Duration ttl, String responseContentType);

    /** Delete a single object (no error if missing). */
    void delete(String key);

    /** Delete all objects under a prefix (e.g. versions/{fileId}/). */
    void deletePrefix(String prefix);

    boolean exists(String key);
}
