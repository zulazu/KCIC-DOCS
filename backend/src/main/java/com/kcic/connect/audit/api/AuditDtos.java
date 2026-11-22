package com.kcic.connect.audit.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {
    }

    public record AuditEventResponse(
            UUID id,
            UUID actorUserId,
            String actorUsername,
            String actorDisplayName,
            String actionCode,
            String resourceType,
            UUID resourceId,
            String outcome,
            String source,
            String requestId,
            Map<String, Object> safeMetadata,
            Instant occurredAt) {
    }
}
