package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_owner_history", schema = "repository")
public class DocumentOwnerHistory {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    private UUID ownerUserId;
    private UUID ownerOrganizationUnitId;

    @Column(nullable = false)
    private Instant validFrom;

    private Instant validUntil;

    @Column(nullable = false)
    private UUID changedBy;

    private String changeReason;

    @Column(nullable = false)
    private Instant createdAt;

    protected DocumentOwnerHistory() {
    }

    public DocumentOwnerHistory(UUID id, UUID documentId, UUID ownerUserId, UUID ownerOrganizationUnitId,
                                UUID changedBy, String changeReason, Instant now) {
        this.id = id;
        this.documentId = documentId;
        this.ownerUserId = ownerUserId;
        this.ownerOrganizationUnitId = ownerOrganizationUnitId;
        this.validFrom = now;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
        this.createdAt = now;
    }

    public void close(Instant now) {
        this.validUntil = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getOwnerOrganizationUnitId() {
        return ownerOrganizationUnitId;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public String getChangeReason() {
        return changeReason;
    }
}
