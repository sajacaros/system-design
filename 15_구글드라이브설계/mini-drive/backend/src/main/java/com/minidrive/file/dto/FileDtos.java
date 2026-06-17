package com.minidrive.file.dto;

import com.minidrive.file.FileEntity;
import jakarta.validation.constraints.Size;

public final class FileDtos {
    private FileDtos() {
    }

    /** List/upload response item (api-contract GET/POST files). */
    public record FileResponse(
            Long id,
            Long folderId,
            String originalName,
            String extension,
            long fileSize,
            int version,
            String status,
            String updatedAt) {
        public static FileResponse from(FileEntity f) {
            return new FileResponse(
                    f.getId(),
                    f.getFolderId(),
                    f.getOriginalName(),
                    f.getExtension(),
                    f.getFileSize(),
                    f.getVersion(),
                    f.getStatus().name(),
                    f.getUpdatedAt() == null ? null : f.getUpdatedAt().toString());
        }
    }

    /**
     * GET /api/files/{id} — meta + downloadUrl.
     * v1.6: downloadUrl = "/api/files/{id}/download" gateway path when UPLOADED, else null
     * (no presigned URL).
     */
    public record FileDetailResponse(
            Long id,
            Long folderId,
            String originalName,
            String extension,
            long fileSize,
            int version,
            String status,
            String updatedAt,
            String downloadUrl) {
        public static FileDetailResponse from(FileEntity f, String downloadUrl) {
            return new FileDetailResponse(
                    f.getId(), f.getFolderId(), f.getOriginalName(), f.getExtension(),
                    f.getFileSize(), f.getVersion(), f.getStatus().name(),
                    f.getUpdatedAt() == null ? null : f.getUpdatedAt().toString(),
                    downloadUrl);
        }
    }

    public record PatchRequest(@Size(max = 255) String originalName, Long folderId) {
    }

    /** POST /api/files/{id}/content response. */
    public record ContentUpdateResponse(Long id, int version, String status) {
    }

    /** POST /api/files/{id}/restore response. */
    public record RestoreResponse(Long id, int version) {
    }

    public record RestoreRequest(Integer version) {
    }

    /** GET /api/files/{id}/versions item. */
    public record VersionResponse(int version, long fileSize, String createdAt) {
    }
}
