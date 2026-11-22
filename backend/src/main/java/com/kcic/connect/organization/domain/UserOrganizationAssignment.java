package com.kcic.connect.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_organization_assignments", schema = "organization")
public class UserOrganizationAssignment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentType assignmentType;

    @Column(nullable = false)
    private Instant validFrom;

    private Instant validUntil;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private UUID assignedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserOrganizationAssignment() {
    }

    public UserOrganizationAssignment(UUID id, UUID userId, UUID organizationUnitId, AssignmentType type,
                                      Instant validFrom, Instant validUntil, UUID assignedBy) {
        this.id = id;
        this.userId = userId;
        this.organizationUnitId = organizationUnitId;
        this.assignmentType = type;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.isActive = true;
        this.assignedBy = assignedBy;
        this.createdAt = validFrom;
    }

    public void end(Instant now) {
        this.isActive = false;
        if (this.validUntil == null || this.validUntil.isAfter(now)) {
            this.validUntil = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public AssignmentType getAssignmentType() {
        return assignmentType;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
