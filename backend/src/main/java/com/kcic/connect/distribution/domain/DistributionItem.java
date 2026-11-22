package com.kcic.connect.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "distribution_items", schema = "distribution")
public class DistributionItem {

    @Id
    private UUID id;

    @Column(name = "distribution_id", nullable = false)
    private UUID distributionId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "document_version_id", nullable = false)
    private UUID documentVersionId;

    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DistributionItem() {
    }

    public DistributionItem(UUID id, UUID distributionId, UUID documentId, UUID documentVersionId,
                            UUID permissionId, Instant now) {
        this.id = id;
        this.distributionId = distributionId;
        this.documentId = documentId;
        this.documentVersionId = documentVersionId;
        this.permissionId = permissionId;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDistributionId() {
        return distributionId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getDocumentVersionId() {
        return documentVersionId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }
}
