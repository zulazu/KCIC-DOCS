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
@Table(name = "distribution_recipients", schema = "distribution")
public class DistributionRecipient {

    @Id
    private UUID id;

    @Column(name = "distribution_id", nullable = false)
    private UUID distributionId;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_status", nullable = false, length = 20)
    private RecipientStatus recipientStatus;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DistributionRecipient() {
    }

    public DistributionRecipient(UUID id, UUID distributionId, UUID recipientUserId, Instant sentAt,
                                 Instant expiresAt) {
        this.id = id;
        this.distributionId = distributionId;
        this.recipientUserId = recipientUserId;
        this.recipientStatus = RecipientStatus.SENT;
        this.sentAt = sentAt;
        this.expiresAt = expiresAt;
        this.createdAt = sentAt;
    }

    public void markRead(Instant now) {
        if (this.recipientStatus == RecipientStatus.SENT) {
            this.recipientStatus = RecipientStatus.READ;
            this.readAt = now;
        }
    }

    public void revoke(Instant now) {
        if (this.recipientStatus.isActive()) {
            this.recipientStatus = RecipientStatus.REVOKED;
            this.revokedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDistributionId() {
        return distributionId;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public RecipientStatus getRecipientStatus() {
        return recipientStatus;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
