package com.minidrive.share;

import com.minidrive.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "share_link", indexes = {
        @Index(name = "idx_share_token", columnList = "token", unique = true),
        @Index(name = "idx_share_file", columnList = "file_id")
})
public class ShareLink extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "expired_at")
    private Instant expiredAt; // null = never expires

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    protected ShareLink() {
    }

    public ShareLink(Long fileId, String token, Instant expiredAt) {
        this.fileId = fileId;
        this.token = token;
        this.expiredAt = expiredAt;
        this.isActive = true;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getToken() {
        return token;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void deactivate() {
        this.isActive = false;
    }

    /** is_active && (expired_at IS NULL OR expired_at > now()) */
    public boolean isAccessible() {
        return isActive && (expiredAt == null || expiredAt.isAfter(Instant.now()));
    }

    public boolean isExpired() {
        return expiredAt != null && !expiredAt.isAfter(Instant.now());
    }
}
