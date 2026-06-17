package com.minidrive.file;

/**
 * File state machine (db-schema.md):
 * PENDING -> UPLOADING -> UPLOADED -> DELETED
 *                    \-> FAILED (retry -> UPLOADING)
 */
public enum FileStatus {
    PENDING,
    UPLOADING,
    UPLOADED,
    FAILED,
    DELETED
}
