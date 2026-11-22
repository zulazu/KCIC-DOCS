package com.kcic.connect.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distributions", schema = "distribution")
public class Distribution {

    @Id
    private UUID id;

    @Column(name = "distribution_code", nullable = false, length = 50)
    private String distributionCode;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "distribution_status", nullable = false, length = 30)
    private DistributionStatus distributionStatus;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected Distribution() {
    }

    public Distribution(UUID id, String distributionCode, UUID senderUserId, String title, String message,
                        Instant expiresAt, Instant now) {
        this.id = id;
        this.distributionCode = distributionCode;
        this.senderUserId = senderUserId;
        this.title = title;
        this.message = message;
        this.distributionStatus = DistributionStatus.SENT;
        this.sentAt = now;
        this.expiresAt = expiresAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void revoke(Instant now) {
        this.distributionStatus = DistributionStatus.REVOKED;
        this.updatedAt = now;
    }

    public void markPartiallyRevoked(Instant now) {
        this.distributionStatus = DistributionStatus.PARTIALLY_REVOKED;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getDistributionCode() {
        return distributionCode;
    }

    public UUID getSenderUserId() {
        return senderUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public DistributionStatus getDistributionStatus() {
        return distributionStatus;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
