package com.minidrive.version;

import com.minidrive.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "file_version",
        uniqueConstraints = @UniqueConstraint(name = "uq_file_version", columnNames = {"file_id", "version"}),
        indexes = @Index(name = "idx_file_version", columnList = "file_id,version"))
public class FileVersion extends BaseEntity {

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(nullable = false)
    private int version;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    protected FileVersion() {
    }

    public FileVersion(Long fileId, String objectKey, int version, long fileSize) {
        this.fileId = fileId;
        this.objectKey = objectKey;
        this.version = version;
        this.fileSize = fileSize;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public int getVersion() {
        return version;
    }

    public long getFileSize() {
        return fileSize;
    }
}
