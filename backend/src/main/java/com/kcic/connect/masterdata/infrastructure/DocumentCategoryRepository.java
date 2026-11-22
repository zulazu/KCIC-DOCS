package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.DocumentCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, UUID> {

    List<DocumentCategory> findAllByOrderByCategoryNameAsc();

    List<DocumentCategory> findAllByActiveTrueOrderByCategoryNameAsc();

    boolean existsByCategoryCodeIgnoreCase(String categoryCode);

    boolean existsByCategoryCodeIgnoreCaseAndIdNot(String categoryCode, UUID id);
}
