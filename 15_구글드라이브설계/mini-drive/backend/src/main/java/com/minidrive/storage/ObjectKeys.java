package com.minidrive.storage;

/**
 * Object key rules (storage-layout.md §Object Key 규칙).
 *   users/{userId}/{fileId}     current version original
 *   versions/{fileId}/v{n}      version n snapshot
 *   thumbnails/{fileId}.png     thumbnail (optional, images only)
 */
public final class ObjectKeys {
    private ObjectKeys() {
    }

    public static String current(long userId, long fileId) {
        return "users/" + userId + "/" + fileId;
    }

    public static String version(long fileId, int n) {
        return "versions/" + fileId + "/v" + n;
    }

    public static String versionPrefix(long fileId) {
        return "versions/" + fileId + "/";
    }

    public static String thumbnail(long fileId) {
        return "thumbnails/" + fileId + ".png";
    }
}
