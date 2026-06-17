package com.minidrive.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByToken(String token);

    List<ShareLink> findByFileId(Long fileId);

    /**
     * v1.4: all share links whose underlying file is owned by the requester.
     * Joins share_link -> file on file_id, filters by owner, newest first.
     * Returns [ShareLink, FileEntity] pairs so the service can read originalName without an N+1.
     */
    @Query("""
            SELECT s, f
            FROM ShareLink s, FileEntity f
            WHERE s.fileId = f.id
              AND f.ownerId = :ownerId
            ORDER BY s.createdAt DESC
            """)
    List<Object[]> findOwnedSharesWithFile(@Param("ownerId") Long ownerId);
}
