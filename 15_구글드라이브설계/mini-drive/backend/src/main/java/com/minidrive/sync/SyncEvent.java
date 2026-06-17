package com.minidrive.sync;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * STOMP event payload (websocket-events.md). Common fields: type, occurredAt.
 * Individual optional fields included only when present.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SyncEvent(
        String type,
        Long fileId,
        String fileName,
        Long folderId,
        Integer version,
        String occurredAt) {

    public static SyncEvent fileUploaded(Long fileId, String fileName, Long folderId) {
        return new SyncEvent("FILE_UPLOADED", fileId, fileName, folderId, null, now());
    }

    public static SyncEvent fileUpdated(Long fileId, Integer version) {
        return new SyncEvent("FILE_UPDATED", fileId, null, null, version, now());
    }

    public static SyncEvent fileDeleted(Long fileId) {
        return new SyncEvent("FILE_DELETED", fileId, null, null, null, now());
    }

    public static SyncEvent shareCreated(Long fileId) {
        return new SyncEvent("SHARE_CREATED", fileId, null, null, null, now());
    }

    private static String now() {
        return Instant.now().toString();
    }
}
