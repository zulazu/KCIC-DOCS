package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.DocumentTagAssignment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTagAssignmentRepository
        extends JpaRepository<DocumentTagAssignment, DocumentTagAssignment.Key> {

    List<DocumentTagAssignment> findAllByIdDocumentId(UUID documentId);

    List<DocumentTagAssignment> findAllByIdDocumentIdIn(Collection<UUID> documentIds);

    void deleteAllByIdDocumentId(UUID documentId);
}
