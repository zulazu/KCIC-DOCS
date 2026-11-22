package com.kcic.connect.audit.application;

import com.kcic.connect.audit.domain.AuditEvent;
import com.kcic.connect.audit.infrastructure.AuditEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(UUID actorUserId, String actionCode, String resourceType, UUID resourceId,
                       String outcome, Map<String, Object> safeMetadata) {
        // Audit never contains credentials, tokens, signed URLs, or document content.
        repository.save(new AuditEvent(UUID.randomUUID(), actorUserId, actionCode, resourceType, resourceId,
                outcome, "WEB", MDC.get("requestId"), safeMetadata == null ? Map.of() : safeMetadata,
                Instant.now(clock)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordStandalone(UUID actorUserId, String actionCode, String resourceType, UUID resourceId,
                                 String outcome, Map<String, Object> safeMetadata) {
        record(actorUserId, actionCode, resourceType, resourceId, outcome, safeMetadata);
    }
}
