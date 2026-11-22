package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.DocumentFavorite;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentFavoriteRepository extends JpaRepository<DocumentFavorite, DocumentFavorite.Key> {

    List<DocumentFavorite> findAllByIdUserIdOrderByCreatedAtDesc(UUID userId);
}
