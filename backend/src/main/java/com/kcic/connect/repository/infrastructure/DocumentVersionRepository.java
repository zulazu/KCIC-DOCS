package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.VersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findAllByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findFirstByDocumentIdAndVersionStatus(UUID documentId, VersionStatus status);

    List<DocumentVersion> findAllByDocumentIdAndVersionStatus(UUID documentId, VersionStatus status);

    List<DocumentVersion> findAllByDocumentIdInAndVersionStatus(Collection<UUID> documentIds, VersionStatus status);

    Optional<DocumentVersion> findByIdAndDocumentId(UUID id, UUID documentId);

    long countByDocumentId(UUID documentId);

    @Query("select coalesce(max(v.versionNumber), 0) from DocumentVersion v where v.documentId = :documentId")
    int maxVersionNumber(@Param("documentId") UUID documentId);
}
