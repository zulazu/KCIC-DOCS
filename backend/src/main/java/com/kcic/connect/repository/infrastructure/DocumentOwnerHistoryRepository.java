package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.DocumentOwnerHistory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentOwnerHistoryRepository extends JpaRepository<DocumentOwnerHistory, UUID> {

    Optional<DocumentOwnerHistory> findFirstByDocumentIdAndValidUntilIsNull(UUID documentId);

    List<DocumentOwnerHistory> findByDocumentIdOrderByValidFromDesc(UUID documentId);
}
