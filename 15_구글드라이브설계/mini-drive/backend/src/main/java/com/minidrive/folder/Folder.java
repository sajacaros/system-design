package com.minidrive.folder;

import com.minidrive.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "folder", indexes = {
        @Index(name = "idx_folder_owner_parent", columnList = "owner_id,parent_id")
})
public class Folder extends BaseEntity {

    @Column(name = "parent_id")
    private Long parentId; // null = root

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 255)
    private String name;

    protected Folder() {
    }

    public Folder(Long parentId, Long ownerId, String name) {
        this.parentId = parentId;
        this.ownerId = ownerId;
        this.name = name;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
