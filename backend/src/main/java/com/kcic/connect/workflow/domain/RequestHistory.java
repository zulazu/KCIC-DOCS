package com.kcic.connect.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "request_histories", schema = "workflow")
public class RequestHistory {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private RequestStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private RequestStatus toStatus;

    @Column(columnDefinition = "text")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected RequestHistory() {
    }

    public RequestHistory(UUID id, UUID requestId, UUID actorUserId, RequestStatus fromStatus,
                          RequestStatus toStatus, String comment, Map<String, Object> metadata,
                          Instant occurredAt) {
        this.id = id;
        this.requestId = requestId;
        this.actorUserId = actorUserId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.comment = comment;
        this.metadata = metadata;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public RequestStatus getFromStatus() {
        return fromStatus;
    }

    public RequestStatus getToStatus() {
        return toStatus;
    }

    public String getComment() {
        return comment;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
