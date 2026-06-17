package com.minidrive.share;

import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import com.minidrive.file.FileEntity;
import com.minidrive.file.FileRepository;
import com.minidrive.file.FileStatus;
import com.minidrive.share.dto.ShareDtos.CreateShareRequest;
import com.minidrive.share.dto.ShareDtos.PublicShareResponse;
import com.minidrive.share.dto.ShareDtos.ShareListItem;
import com.minidrive.share.dto.ShareDtos.ShareResponse;
import com.minidrive.sync.SyncEvent;
import com.minidrive.sync.SyncEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShareLinkRepository shareRepository;
    private final FileRepository fileRepository;
    private final SyncEventPublisher events;

    public ShareService(ShareLinkRepository shareRepository,
                        FileRepository fileRepository,
                        SyncEventPublisher events) {
        this.shareRepository = shareRepository;
        this.fileRepository = fileRepository;
        this.events = events;
    }

    @Transactional
    public ShareResponse createShare(Long ownerId, Long fileId, CreateShareRequest req) {
        FileEntity file = requireOwnedFile(fileId, ownerId);
        if (file.getStatus() != FileStatus.UPLOADED) {
            throw new ApiException(ErrorCode.CONFLICT, "Only UPLOADED files can be shared");
        }
        Instant expiredAt = parseInstant(req == null ? null : req.expiredAt());
        ShareLink link = new ShareLink(fileId, generateToken(), expiredAt);
        link = shareRepository.save(link);

        events.publish(ownerId, SyncEvent.shareCreated(fileId), "Share link created for " + file.getOriginalName());
        return new ShareResponse(
                link.getId(),
                link.getToken(),
                "/share/" + link.getToken(),
                link.getExpiredAt() == null ? null : link.getExpiredAt().toString(),
                link.isActive());
    }

    /**
     * v1.4: list every share link belonging to a file owned by the requester.
     * Authorization is enforced at query level (owner_id == requester) so no other
     * user's shares are ever exposed. Sorted createdAt DESC.
     */
    @Transactional(readOnly = true)
    public List<ShareListItem> listOwnShares(Long ownerId) {
        List<Object[]> rows = shareRepository.findOwnedSharesWithFile(ownerId);
        List<ShareListItem> result = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            ShareLink link = (ShareLink) row[0];
            FileEntity file = (FileEntity) row[1];
            result.add(new ShareListItem(
                    link.getId(),
                    file.getId(),
                    file.getOriginalName(),
                    link.getToken(),
                    "/share/" + link.getToken(),
                    link.getExpiredAt() == null ? null : link.getExpiredAt().toString(),
                    link.isActive(),
                    link.getCreatedAt() == null ? null : link.getCreatedAt().toString()));
        }
        return result;
    }

    /**
     * Public, unauthenticated metadata read. INVALID_LINK / EXPIRED / DISABLED per contract.
     * <p>v1.6: {@code downloadUrl} is the gateway download path (relative), not a presigned URL.
     * The validity captured here is advisory only — the actual authorization is re-checked on
     * every request by {@link #resolveForDownload(String)}, so a link disabled after this call
     * still blocks the download.
     */
    @Transactional(readOnly = true)
    public PublicShareResponse resolve(String token) {
        FileEntity file = validateAndGetFile(token);
        String downloadUrl = "/api/public/share/" + token + "/download";
        return new PublicShareResponse(file.getId(), file.getOriginalName(), file.getExtension(),
                file.getFileSize(), downloadUrl);
    }

    /**
     * v1.6 gateway download: re-validate the link on EVERY request (token valid AND is_active
     * AND not expired AND file UPLOADED) and return the file to stream. This is the single
     * authorization choke point — disabling/expiry is reflected immediately (no presign-TTL
     * bypass). INVALID_LINK / DISABLED / EXPIRED per contract.
     */
    @Transactional(readOnly = true)
    public FileEntity resolveForDownload(String token) {
        return validateAndGetFile(token);
    }

    /** Shared validation for both metadata resolve and gateway download. */
    private FileEntity validateAndGetFile(String token) {
        ShareLink link = shareRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_LINK, "Invalid share link"));
        if (!link.isActive()) {
            throw new ApiException(ErrorCode.DISABLED, "Share link disabled");
        }
        if (link.isExpired()) {
            throw new ApiException(ErrorCode.EXPIRED, "Share link expired");
        }
        FileEntity file = fileRepository.findById(link.getFileId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_LINK, "Shared file not found"));
        if (file.getStatus() != FileStatus.UPLOADED) {
            throw new ApiException(ErrorCode.INVALID_LINK, "Shared file is not available");
        }
        return file;
    }

    @Transactional
    public void disable(Long ownerId, Long shareId) {
        ShareLink link = shareRepository.findById(shareId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Share link not found"));
        // Authorization via file ownership.
        FileEntity file = fileRepository.findById(link.getFileId())
                .orElseThrow(ApiException::fileNotFound);
        if (!file.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden();
        }
        link.deactivate();
    }

    private FileEntity requireOwnedFile(Long id, Long ownerId) {
        FileEntity file = fileRepository.findById(id).orElseThrow(ApiException::fileNotFound);
        if (!file.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden();
        }
        return file;
    }

    private static String generateToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private static Instant parseInstant(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(iso);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.VALIDATION, "Invalid expiredAt format (ISO-8601 expected)");
        }
    }
}
