package com.minidrive.folder.dto;

import com.minidrive.folder.Folder;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class FolderDtos {
    private FolderDtos() {
    }

    public record CreateRequest(Long parentId, @NotBlank @Size(max = 255) String name) {
    }

    public record UpdateRequest(@Size(max = 255) String name, Long parentId) {
    }

    public record FolderResponse(Long id, Long parentId, String name, String createdAt) {
        public static FolderResponse from(Folder f) {
            return new FolderResponse(
                    f.getId(),
                    f.getParentId(),
                    f.getName(),
                    f.getCreatedAt() == null ? null : f.getCreatedAt().toString());
        }
    }
}
