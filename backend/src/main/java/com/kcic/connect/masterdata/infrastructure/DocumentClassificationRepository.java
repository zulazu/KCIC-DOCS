package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.DocumentClassification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentClassificationRepository extends JpaRepository<DocumentClassification, UUID> {

    List<DocumentClassification> findAllByOrderBySensitivityRankAsc();

    List<DocumentClassification> findAllByActiveTrueOrderBySensitivityRankAsc();

    boolean existsByClassificationCodeIgnoreCase(String classificationCode);

    boolean existsByClassificationCodeIgnoreCaseAndIdNot(String classificationCode, UUID id);
}
