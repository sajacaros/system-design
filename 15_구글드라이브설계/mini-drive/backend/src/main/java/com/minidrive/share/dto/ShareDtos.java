package com.minidrive.share.dto;

public final class ShareDtos {
    private ShareDtos() {
    }

    public record CreateShareRequest(String expiredAt) {
    }

    public record ShareResponse(Long id, String token, String url, String expiredAt, boolean isActive) {
    }

    /**
     * v1.4 GET /api/shares item: a share link enriched with its file's name.
     * url = "/share/{token}" (relative); host is composed by the frontend.
     */
    public record ShareListItem(
            Long id,
            Long fileId,
            String fileName,
            String token,
            String url,
            String expiredAt,
            boolean isActive,
            String createdAt) {
    }

    /**
     * Public GET /api/public/share/{token} response: file meta + gateway download path.
     * v1.6: downloadUrl = "/api/public/share/{token}/download" (relative gateway path), not a
     * presigned URL. Actual authorization is re-checked per request by the download endpoint.
     */
    public record PublicShareResponse(
            Long id,
            String originalName,
            String extension,
            long fileSize,
            String downloadUrl) {
    }
}
