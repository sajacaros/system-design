package com.minidrive.folder;

import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import com.minidrive.file.FileEntity;
import com.minidrive.file.FileRepository;
import com.minidrive.file.FileStatus;
import com.minidrive.folder.dto.FolderDtos.CreateRequest;
import com.minidrive.folder.dto.FolderDtos.FolderResponse;
import com.minidrive.folder.dto.FolderDtos.UpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final FileRepository fileRepository;

    public FolderService(FolderRepository folderRepository, FileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> list(Long ownerId, Long parentId) {
        List<Folder> folders = (parentId == null)
                ? folderRepository.findByOwnerIdAndParentIdIsNullOrderByNameAsc(ownerId)
                : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, parentId);
        return folders.stream().map(FolderResponse::from).toList();
    }

    @Transactional
    public FolderResponse create(Long ownerId, CreateRequest req) {
        if (req.parentId() != null) {
            requireOwnedFolder(req.parentId(), ownerId);
        }
        requireUniqueName(ownerId, req.parentId(), req.name());
        Folder folder = new Folder(req.parentId(), ownerId, req.name());
        return FolderResponse.from(folderRepository.save(folder));
    }

    @Transactional
    public FolderResponse update(Long ownerId, Long id, UpdateRequest req) {
        Folder folder = requireOwnedFolder(id, ownerId);
        Long newParent = req.parentId() != null ? req.parentId() : folder.getParentId();
        String newName = req.name() != null ? req.name() : folder.getName();

        // Move target
        if (req.parentId() != null && !req.parentId().equals(folder.getParentId())) {
            requireOwnedFolder(req.parentId(), ownerId);
            if (wouldCreateCycle(id, req.parentId())) {
                throw new ApiException(ErrorCode.CYCLIC_MOVE, "Cannot move a folder into itself or its descendant");
            }
        }

        boolean nameChanged = !newName.equals(folder.getName());
        boolean parentChanged = !java.util.Objects.equals(newParent, folder.getParentId());
        if (nameChanged || parentChanged) {
            // Check name uniqueness in destination (excluding self).
            if (existsSiblingName(ownerId, newParent, newName, id)) {
                throw ApiException.nameConflict();
            }
        }

        folder.setName(newName);
        folder.setParentId(newParent);
        return FolderResponse.from(folder);
    }

    @Transactional
    public void delete(Long ownerId, Long id) {
        Folder folder = requireOwnedFolder(id, ownerId);
        cascadeTrash(ownerId, folder);
    }

    /** Recursively move folder subtree's files to trash (DELETED). Folders are removed structurally. */
    private void cascadeTrash(Long ownerId, Folder root) {
        Deque<Folder> stack = new ArrayDeque<>();
        stack.push(root);
        Instant now = Instant.now();
        while (!stack.isEmpty()) {
            Folder cur = stack.pop();
            // Trash files in this folder
            for (FileEntity f : fileRepository.findByFolderIdAndStatusNot(cur.getId(), FileStatus.DELETED)) {
                f.setStatus(FileStatus.DELETED);
                f.setDeletedAt(now);
            }
            // Descend
            for (Folder child : folderRepository.findByOwnerIdAndParentId(ownerId, cur.getId())) {
                stack.push(child);
            }
        }
        // Remove the folder subtree (folders carry no trash state; their files are DELETED).
        deleteSubtree(ownerId, root);
    }

    private void deleteSubtree(Long ownerId, Folder root) {
        Deque<Folder> order = new ArrayDeque<>();
        Deque<Folder> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            Folder cur = stack.pop();
            order.push(cur);
            for (Folder child : folderRepository.findByOwnerIdAndParentId(ownerId, cur.getId())) {
                stack.push(child);
            }
        }
        // order has deepest last-pushed -> delete leaves first
        while (!order.isEmpty()) {
            folderRepository.delete(order.pop());
        }
    }

    private boolean wouldCreateCycle(Long movingId, Long targetParentId) {
        Long cursor = targetParentId;
        while (cursor != null) {
            if (cursor.equals(movingId)) {
                return true;
            }
            Folder parent = folderRepository.findById(cursor).orElse(null);
            cursor = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private Folder requireOwnedFolder(Long id, Long ownerId) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(ApiException::folderNotFound);
        if (!folder.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden();
        }
        return folder;
    }

    private void requireUniqueName(Long ownerId, Long parentId, String name) {
        boolean exists = (parentId == null)
                ? folderRepository.existsByOwnerIdAndParentIdIsNullAndName(ownerId, name)
                : folderRepository.existsByOwnerIdAndParentIdAndName(ownerId, parentId, name);
        if (exists) {
            throw ApiException.nameConflict();
        }
    }

    private boolean existsSiblingName(Long ownerId, Long parentId, String name, Long excludeId) {
        List<Folder> siblings = (parentId == null)
                ? folderRepository.findByOwnerIdAndParentIdIsNullOrderByNameAsc(ownerId)
                : folderRepository.findByOwnerIdAndParentIdOrderByNameAsc(ownerId, parentId);
        return siblings.stream().anyMatch(f -> f.getName().equals(name) && !f.getId().equals(excludeId));
    }
}
