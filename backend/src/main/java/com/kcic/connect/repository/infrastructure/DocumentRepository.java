package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.Document;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, UUID>, JpaSpecificationExecutor<Document> {

    @Query("select (count(d) > 0) from Document d where lower(d.documentCode) = lower(:documentCode)")
    boolean existsByDocumentCodeIgnoreCase(@Param("documentCode") String documentCode);

    @Query("""
            select (count(d) > 0) from Document d
            where lower(d.documentCode) = lower(:documentCode) and d.id <> :excludeId
            """)
    boolean existsByDocumentCodeIgnoreCaseExcluding(@Param("documentCode") String documentCode,
                                                    @Param("excludeId") UUID excludeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Document d where d.id = :id")
    Optional<Document> findWithLockById(@Param("id") UUID id);
}
