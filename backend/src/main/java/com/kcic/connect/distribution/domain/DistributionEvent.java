package com.kcic.connect.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distribution_events", schema = "distribution")
public class DistributionEvent {

    @Id
    private UUID id;

    @Column(name = "distribution_id", nullable = false)
    private UUID distributionId;

    @Column(name = "distribution_recipient_id")
    private UUID distributionRecipientId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private DistributionEventType eventType;

    @Column(name = "event_note", columnDefinition = "text")
    private String eventNote;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected DistributionEvent() {
    }

    public DistributionEvent(UUID id, UUID distributionId, UUID distributionRecipientId, UUID actorUserId,
                             DistributionEventType eventType, String eventNote, Instant occurredAt) {
        this.id = id;
        this.distributionId = distributionId;
        this.distributionRecipientId = distributionRecipientId;
        this.actorUserId = actorUserId;
        this.eventType = eventType;
        this.eventNote = eventNote;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDistributionId() {
        return distributionId;
    }

    public UUID getDistributionRecipientId() {
        return distributionRecipientId;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public DistributionEventType getEventType() {
        return eventType;
    }

    public String getEventNote() {
        return eventNote;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
