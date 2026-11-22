package com.kcic.connect.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_events", schema = "audit")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(nullable = false, length = 100)
    private String actionCode;

    @Column(nullable = false, length = 50)
    private String resourceType;

    @Column
    private UUID resourceId;

    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(nullable = false, length = 40)
    private String source;

    @Column(length = 100)
    private String requestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_metadata")
    private Map<String, Object> safeMetadata;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, UUID actorUserId, String actionCode, String resourceType, UUID resourceId,
                      String outcome, String source, String requestId, Map<String, Object> safeMetadata,
                      Instant occurredAt) {
        this.id = id;
        this.actorUserId = actorUserId;
        this.actionCode = actionCode;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.outcome = outcome;
        this.source = source;
        this.requestId = requestId;
        this.safeMetadata = safeMetadata;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getSource() {
        return source;
    }

    public String getRequestId() {
        return requestId;
    }

    public Map<String, Object> getSafeMetadata() {
        return safeMetadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
