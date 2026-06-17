package com.minidrive.file;

import com.minidrive.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "file", indexes = {
        @Index(name = "idx_file_owner_folder", columnList = "owner_id,folder_id"),
        @Index(name = "idx_file_owner_name", columnList = "owner_id,original_name"),
        @Index(name = "idx_file_owner_ext", columnList = "owner_id,extension"),
        @Index(name = "idx_file_owner_status", columnList = "owner_id,status")
})
public class FileEntity extends BaseEntity {

    @Column(name = "folder_id")
    private Long folderId; // null = root

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(length = 50)
    private String extension;

    @Column(name = "file_size", nullable = false)
    private long fileSize = 0L;

    @Column(nullable = false)
    private int version = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected FileEntity() {
    }

    public FileEntity(Long folderId, Long ownerId, String originalName, String extension) {
        this.folderId = folderId;
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.extension = extension;
        this.status = FileStatus.PENDING;
        this.version = 1;
        this.fileSize = 0L;
        this.objectKey = "";
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
