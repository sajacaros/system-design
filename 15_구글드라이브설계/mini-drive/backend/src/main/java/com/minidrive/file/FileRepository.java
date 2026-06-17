package com.minidrive.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileEntity, Long>, JpaSpecificationExecutor<FileEntity> {

    Optional<FileEntity> findByIdAndOwnerId(Long id, Long ownerId);

    boolean existsByOwnerIdAndFolderIdAndOriginalNameAndStatusNot(
            Long ownerId, Long folderId, String originalName, FileStatus status);

    boolean existsByOwnerIdAndFolderIdIsNullAndOriginalNameAndStatusNot(
            Long ownerId, String originalName, FileStatus status);

    List<FileEntity> findByFolderIdAndStatusNot(Long folderId, FileStatus status);

    List<FileEntity> findByOwnerIdAndFolderIdAndStatusNot(Long ownerId, Long folderId, FileStatus status);

    List<FileEntity> findByOwnerIdAndFolderIdIsNullAndStatusNot(Long ownerId, FileStatus status);
}
