package com.kcic.connect.template.infrastructure;

import com.kcic.connect.template.domain.DocumentTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, UUID>,
        JpaSpecificationExecutor<DocumentTemplate> {

    Optional<DocumentTemplate> findByDocumentId(UUID documentId);

    @Query("select (count(t) > 0) from DocumentTemplate t where lower(t.templateCode) = lower(:templateCode)")
    boolean existsByTemplateCodeIgnoreCase(@Param("templateCode") String templateCode);

    @Query("""
            select (count(t) > 0) from DocumentTemplate t
            where lower(t.templateCode) = lower(:templateCode) and t.id <> :excludeId
            """)
    boolean existsByTemplateCodeIgnoreCaseExcluding(@Param("templateCode") String templateCode,
                                                    @Param("excludeId") UUID excludeId);
}
