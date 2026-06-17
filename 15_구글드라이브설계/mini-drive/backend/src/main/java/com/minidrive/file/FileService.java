package com.minidrive.file;

import com.minidrive.auth.CurrentUser;
import com.minidrive.common.ApiException;
import com.minidrive.common.ErrorCode;
import com.minidrive.common.PageResponse;
import com.minidrive.file.dto.FileDtos.ContentUpdateResponse;
import com.minidrive.file.dto.FileDtos.FileDetailResponse;
import com.minidrive.file.dto.FileDtos.FileResponse;
import com.minidrive.file.dto.FileDtos.PatchRequest;
import com.minidrive.file.dto.FileDtos.RestoreResponse;
import com.minidrive.file.dto.FileDtos.VersionResponse;
import com.minidrive.folder.Folder;
import com.minidrive.folder.FolderRepository;
import com.minidrive.storage.ContentTypes;
import com.minidrive.storage.ObjectKeys;
import com.minidrive.storage.StorageService;
import com.minidrive.sync.SyncEvent;
import com.minidrive.sync.SyncEventPublisher;
import com.minidrive.version.FileVersion;
import com.minidrive.version.FileVersionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FolderRepository folderRepository;
    private final StorageService storage;
    private final SyncEventPublisher events;

    public FileService(FileRepository fileRepository,
                       FileVersionRepository versionRepository,
                       FolderRepository folderRepository,
                       StorageService storage,
                       SyncEventPublisher events) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.folderRepository = folderRepository;
        this.storage = storage;
        this.events = events;
    }

    // ----- Listing & search -----

    @Transactional(readOnly = true)
    public PageResponse<FileResponse> list(Long ownerId, Long folderId,
                                           FileStatus status, String name, String extension, String sort,
                                           int page, int size) {
        // Trash is a folder-agnostic global view: when listing DELETED files without a
        // specific numeric folderId, return the owner's DELETED files across all folders.
        boolean globalTrash = status == FileStatus.DELETED && folderId == null;
        Specification<FileEntity> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), ownerId));
            ps.add(cb.equal(root.get("status"), status));
            // folderId filter only when not searching globally by name/extension,
            // and not when showing the global trash view.
            if (name == null && extension == null && !globalTrash) {
                if (folderId == null) {
                    ps.add(cb.isNull(root.get("folderId")));
                } else {
                    ps.add(cb.equal(root.get("folderId"), folderId));
                }
            }
            if (name != null && !name.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("originalName")), "%" + name.toLowerCase() + "%"));
            }
            if (extension != null && !extension.isBlank()) {
                ps.add(cb.equal(root.get("extension"), extension));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        Sort sortSpec = "recent".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.DESC, "updatedAt")
                : Sort.by(Sort.Direction.ASC, "originalName");
        Pageable pageable = PageRequest.of(page, size, sortSpec);
        Page<FileEntity> result = fileRepository.findAll(spec, pageable);
        return PageResponse.from(result, FileResponse::from);
    }

    // ----- Upload (new file) -----

    @Transactional
    public FileResponse upload(Long ownerId, Long folderId, MultipartFile file) {
        if (folderId != null) {
            requireOwnedFolder(folderId, ownerId);
        }
        String originalName = file.getOriginalFilename() == null ? "untitled" : file.getOriginalFilename();
        // NAME_CONFLICT: same folder, same name, not-deleted file already exists.
        boolean conflict = (folderId == null)
                ? fileRepository.existsByOwnerIdAndFolderIdIsNullAndOriginalNameAndStatusNot(
                        ownerId, originalName, FileStatus.DELETED)
                : fileRepository.existsByOwnerIdAndFolderIdAndOriginalNameAndStatusNot(
                        ownerId, folderId, originalName, FileStatus.DELETED);
        if (conflict) {
            throw ApiException.nameConflict();
        }

        // PENDING -> UPLOADING
        FileEntity entity = new FileEntity(folderId, ownerId, originalName, extensionOf(originalName));
        entity.setStatus(FileStatus.UPLOADING);
        entity = fileRepository.save(entity); // need id for object key

        String currentKey = ObjectKeys.current(ownerId, entity.getId());
        String versionKey = ObjectKeys.version(entity.getId(), 1);
        try {
            byte[] bytes = file.getBytes();
            String storedType = ContentTypes.normalizeForStorage(file.getContentType(), originalName);
            // Store current + version snapshot.
            storage.put(currentKey, new java.io.ByteArrayInputStream(bytes), bytes.length, storedType);
            storage.put(versionKey, new java.io.ByteArrayInputStream(bytes), bytes.length, storedType);
            entity.setObjectKey(currentKey);
            entity.setFileSize(bytes.length);
            entity.setVersion(1);
            entity.setStatus(FileStatus.UPLOADED);
            versionRepository.save(new FileVersion(entity.getId(), versionKey, 1, bytes.length));
        } catch (IOException | RuntimeException e) {
            entity.setStatus(FileStatus.FAILED);
            throw new ApiException(ErrorCode.VALIDATION, "Upload failed: " + e.getMessage());
        }

        events.publish(ownerId, SyncEvent.fileUploaded(entity.getId(), entity.getOriginalName(), folderId),
                "File uploaded: " + entity.getOriginalName());
        return FileResponse.from(entity);
    }

    // ----- New version (= content update / edit) -----

    @Transactional
    public ContentUpdateResponse uploadNewVersion(Long ownerId, Long id, MultipartFile file,
                                                  Integer baseVersion, boolean overwrite) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        requireUploaded(entity);

        // Conflict handling: baseVersion must equal current version unless overwrite.
        if (!overwrite) {
            if (baseVersion == null) {
                throw new ApiException(ErrorCode.VALIDATION, "baseVersion is required");
            }
            if (baseVersion != entity.getVersion()) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Version conflict: base " + baseVersion + " != current " + entity.getVersion());
            }
        }

        int newVersion = entity.getVersion() + 1;
        String versionKey = ObjectKeys.version(id, newVersion);
        String currentKey = ObjectKeys.current(ownerId, id);
        try {
            byte[] bytes = file.getBytes();
            String storedType = ContentTypes.normalizeForStorage(file.getContentType(), entity.getOriginalName());
            storage.put(versionKey, new java.io.ByteArrayInputStream(bytes), bytes.length, storedType);
            storage.put(currentKey, new java.io.ByteArrayInputStream(bytes), bytes.length, storedType);
            versionRepository.save(new FileVersion(id, versionKey, newVersion, bytes.length));
            entity.setVersion(newVersion);
            entity.setObjectKey(currentKey);
            entity.setFileSize(bytes.length);
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.VALIDATION, "New version upload failed: " + e.getMessage());
        }

        events.publish(ownerId, SyncEvent.fileUpdated(id, newVersion), "New version: " + entity.getOriginalName());
        return new ContentUpdateResponse(id, newVersion, entity.getStatus().name());
    }

    // ----- Get detail with gateway download path (v1.6) -----

    @Transactional(readOnly = true)
    public FileDetailResponse get(Long ownerId, Long id) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        // v1.6: downloadUrl is the gateway download path (not a presigned URL); null unless UPLOADED.
        String url = entity.getStatus() == FileStatus.UPLOADED
                ? "/api/files/" + id + "/download"
                : null;
        return FileDetailResponse.from(entity, url);
    }

    /**
     * v1.6 gateway download authorization for the current version: re-validate owner + UPLOADED
     * on every request and return the file for streaming. 403 FORBIDDEN (non-owner) /
     * 404 NOT_FOUND (missing/deleted/not UPLOADED).
     */
    @Transactional(readOnly = true)
    public FileEntity resolveForDownload(Long ownerId, Long id) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        if (entity.getStatus() != FileStatus.UPLOADED) {
            throw new ApiException(ErrorCode.FILE_NOT_FOUND, "File is not available for download");
        }
        return entity;
    }

    // ----- Rename / move -----

    @Transactional
    public FileResponse patch(Long ownerId, Long id, PatchRequest req) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        Long newFolder = req.folderId() != null ? req.folderId() : entity.getFolderId();
        String newName = req.originalName() != null ? req.originalName() : entity.getOriginalName();

        if (req.folderId() != null && !req.folderId().equals(entity.getFolderId())) {
            requireOwnedFolder(req.folderId(), ownerId);
        }

        boolean changed = !newName.equals(entity.getOriginalName())
                || !java.util.Objects.equals(newFolder, entity.getFolderId());
        if (changed && nameTakenInFolder(ownerId, newFolder, newName, id)) {
            throw ApiException.nameConflict();
        }
        entity.setOriginalName(newName);
        entity.setExtension(extensionOf(newName));
        entity.setFolderId(newFolder);

        events.publish(ownerId, SyncEvent.fileUpdated(id, entity.getVersion()),
                "File updated: " + entity.getOriginalName());
        return FileResponse.from(entity);
    }

    // ----- Soft delete (trash) -----

    @Transactional
    public void delete(Long ownerId, Long id) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        entity.setStatus(FileStatus.DELETED);
        entity.setDeletedAt(Instant.now());
        events.publish(ownerId, SyncEvent.fileDeleted(id), "File moved to trash");
    }

    // ----- Versions -----

    @Transactional(readOnly = true)
    public List<VersionResponse> listVersions(Long ownerId, Long id) {
        requireOwnedFile(id, ownerId);
        return versionRepository.findByFileIdOrderByVersionDesc(id).stream()
                .map(v -> new VersionResponse(v.getVersion(), v.getFileSize(),
                        v.getCreatedAt() == null ? null : v.getCreatedAt().toString()))
                .toList();
    }

    /**
     * v1.6 gateway download authorization for a specific version: re-validate owner on every
     * request and return the version's object key + display name for streaming.
     * 403 FORBIDDEN (non-owner) / 404 NOT_FOUND (file/version missing).
     */
    @Transactional(readOnly = true)
    public VersionDownloadTarget resolveVersionForDownload(Long ownerId, Long id, int version) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        FileVersion v = versionRepository.findByFileIdAndVersion(id, version)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Version not found"));
        return new VersionDownloadTarget(v.getObjectKey(), entity.getOriginalName());
    }

    /** Object key + display name resolved for a version gateway download. */
    public record VersionDownloadTarget(String objectKey, String originalName) {
    }

    // ----- Restore: trash-restore OR version-restore -----

    @Transactional
    public RestoreResponse restore(Long ownerId, Long id, Integer version) {
        FileEntity entity = requireOwnedFile(id, ownerId);

        if (version != null) {
            // Version restore: copy the requested version into a new current version.
            FileVersion target = versionRepository.findByFileIdAndVersion(id, version)
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Version not found"));
            int newVersion = entity.getVersion() + 1;
            String newVersionKey = ObjectKeys.version(id, newVersion);
            String currentKey = ObjectKeys.current(ownerId, id);
            storage.copy(target.getObjectKey(), newVersionKey);
            storage.copy(target.getObjectKey(), currentKey);
            versionRepository.save(new FileVersion(id, newVersionKey, newVersion, target.getFileSize()));
            entity.setVersion(newVersion);
            entity.setObjectKey(currentKey);
            entity.setFileSize(target.getFileSize());
            events.publish(ownerId, SyncEvent.fileUpdated(id, newVersion),
                    "Restored version " + version + " of " + entity.getOriginalName());
            return new RestoreResponse(id, newVersion);
        }

        // Trash restore.
        if (entity.getStatus() != FileStatus.DELETED) {
            throw new ApiException(ErrorCode.CONFLICT, "File is not in trash");
        }
        entity.setStatus(FileStatus.UPLOADED);
        entity.setDeletedAt(null);
        events.publish(ownerId, SyncEvent.fileUpdated(id, entity.getVersion()),
                "Restored from trash: " + entity.getOriginalName());
        return new RestoreResponse(id, entity.getVersion());
    }

    // ----- Permanent delete (trash only) -----

    @Transactional
    public void permanentDelete(Long ownerId, Long id) {
        FileEntity entity = requireOwnedFile(id, ownerId);
        if (entity.getStatus() != FileStatus.DELETED) {
            throw new ApiException(ErrorCode.CONFLICT, "File is not in trash");
        }
        // Remove MinIO objects: current + all versions + thumbnail.
        storage.delete(ObjectKeys.current(ownerId, id));
        storage.deletePrefix(ObjectKeys.versionPrefix(id));
        storage.delete(ObjectKeys.thumbnail(id));
        versionRepository.deleteByFileId(id);
        fileRepository.delete(entity);
    }

    // ----- Helpers -----

    private FileEntity requireOwnedFile(Long id, Long ownerId) {
        FileEntity entity = fileRepository.findById(id).orElseThrow(ApiException::fileNotFound);
        if (!entity.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden();
        }
        return entity;
    }

    private void requireUploaded(FileEntity entity) {
        if (entity.getStatus() != FileStatus.UPLOADED) {
            throw new ApiException(ErrorCode.CONFLICT, "File is not in UPLOADED state");
        }
    }

    private Folder requireOwnedFolder(Long folderId, Long ownerId) {
        Folder folder = folderRepository.findById(folderId).orElseThrow(ApiException::folderNotFound);
        if (!folder.getOwnerId().equals(ownerId)) {
            throw ApiException.forbidden();
        }
        return folder;
    }

    private boolean nameTakenInFolder(Long ownerId, Long folderId, String name, Long excludeId) {
        List<FileEntity> files = (folderId == null)
                ? fileRepository.findByOwnerIdAndFolderIdIsNullAndStatusNot(ownerId, FileStatus.DELETED)
                : fileRepository.findByOwnerIdAndFolderIdAndStatusNot(ownerId, folderId, FileStatus.DELETED);
        return files.stream().anyMatch(f -> f.getOriginalName().equals(name) && !f.getId().equals(excludeId));
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase();
    }
}
