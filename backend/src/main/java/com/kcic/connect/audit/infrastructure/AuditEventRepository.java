package com.kcic.connect.audit.infrastructure;

import com.kcic.connect.audit.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>,
        JpaSpecificationExecutor<AuditEvent> {

    List<AuditEvent> findTop50ByResourceTypeAndResourceIdOrderByOccurredAtDesc(String resourceType,
                                                                               UUID resourceId);
}
