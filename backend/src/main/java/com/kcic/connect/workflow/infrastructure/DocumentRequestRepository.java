package com.kcic.connect.workflow.infrastructure;

import com.kcic.connect.workflow.domain.DocumentRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocumentRequestRepository
        extends JpaRepository<DocumentRequest, UUID>, JpaSpecificationExecutor<DocumentRequest> {

    boolean existsByRequestCode(String requestCode);
}
