package com.kcic.connect.distribution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Provenance link between a distribution recipient/item and the ACL grant created
 * for it. Revoke follows these rows only, so independent grants stay untouched.
 */
@Entity
@Table(name = "distribution_access_grants", schema = "distribution")
public class DistributionAccessGrant {

    @Id
    private UUID id;

    @Column(name = "distribution_recipient_id", nullable = false)
    private UUID distributionRecipientId;

    @Column(name = "distribution_item_id", nullable = false)
    private UUID distributionItemId;

    @Column(name = "resource_permission_id", nullable = false)
    private UUID resourcePermissionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DistributionAccessGrant() {
    }

    public DistributionAccessGrant(UUID id, UUID distributionRecipientId, UUID distributionItemId,
                                   UUID resourcePermissionId, Instant now) {
        this.id = id;
        this.distributionRecipientId = distributionRecipientId;
        this.distributionItemId = distributionItemId;
        this.resourcePermissionId = resourcePermissionId;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDistributionRecipientId() {
        return distributionRecipientId;
    }

    public UUID getDistributionItemId() {
        return distributionItemId;
    }

    public UUID getResourcePermissionId() {
        return resourcePermissionId;
    }
}
