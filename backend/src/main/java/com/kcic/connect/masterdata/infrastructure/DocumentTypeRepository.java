package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.DocumentType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, UUID> {

    List<DocumentType> findAllByOrderByTypeNameAsc();

    List<DocumentType> findAllByActiveTrueOrderByTypeNameAsc();

    boolean existsByTypeCodeIgnoreCase(String typeCode);

    boolean existsByTypeCodeIgnoreCaseAndIdNot(String typeCode, UUID id);
}
