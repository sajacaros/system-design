package com.minidrive.share;

import com.minidrive.auth.CurrentUser;
import com.minidrive.file.FileEntity;
import com.minidrive.share.dto.ShareDtos.CreateShareRequest;
import com.minidrive.share.dto.ShareDtos.PublicShareResponse;
import com.minidrive.share.dto.ShareDtos.ShareListItem;
import com.minidrive.share.dto.ShareDtos.ShareResponse;
import com.minidrive.storage.GatewayDownload;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ShareController {

    private final ShareService shareService;
    private final GatewayDownload gatewayDownload;

    public ShareController(ShareService shareService, GatewayDownload gatewayDownload) {
        this.shareService = shareService;
        this.gatewayDownload = gatewayDownload;
    }

    @PostMapping("/api/files/{id}/share")
    @ResponseStatus(HttpStatus.CREATED)
    public ShareResponse create(@PathVariable Long id, @RequestBody(required = false) CreateShareRequest req) {
        return shareService.createShare(CurrentUser.id(), id, req);
    }

    /** v1.4: list the requester's own share links (owner-scoped). */
    @GetMapping("/api/shares")
    public List<ShareListItem> listMine() {
        return shareService.listOwnShares(CurrentUser.id());
    }

    @DeleteMapping("/api/share/{id}")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        shareService.disable(CurrentUser.id(), id);
        return ResponseEntity.noContent().build();
    }

    /** Public, unauthenticated read-only access (v1.2: moved under /api/public to avoid SPA route conflict). */
    @GetMapping("/api/public/share/{token}")
    public PublicShareResponse resolve(@PathVariable String token) {
        return shareService.resolve(token);
    }

    /**
     * v1.6 gateway-download (public, unauthenticated). Re-validates the link on EVERY request
     * (active + not expired + file UPLOADED) — disabling/expiry is reflected immediately. On
     * success returns an empty-body 200 with X-Accel-Redirect; nginx streams from MinIO.
     * Errors: 404 INVALID_LINK / 410 DISABLED / 410 EXPIRED.
     */
    @GetMapping("/api/public/share/{token}/download")
    public ResponseEntity<Void> download(@PathVariable String token) {
        FileEntity file = shareService.resolveForDownload(token);
        return gatewayDownload.redirect(file.getObjectKey(), file.getOriginalName());
    }
}
