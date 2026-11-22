package com.kcic.connect.authorization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments", schema = "`authorization`")
public class UserRoleAssignment {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ScopeType scopeType;

    private UUID organizationUnitId;
    private UUID workspaceId;

    @Column(nullable = false)
    private boolean includeDescendants;

    @Column(nullable = false)
    private Instant validFrom;

    private Instant validUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssignmentStatus assignmentStatus;

    @Column(nullable = false)
    private UUID assignedBy;

    private UUID revokedBy;
    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(UUID id, UUID userId, UUID roleId, ScopeType scopeType, UUID organizationUnitId,
                              UUID workspaceId, boolean includeDescendants, Instant validFrom, Instant validUntil,
                              UUID assignedBy) {
        this.id = id;
        this.userId = userId;
        this.roleId = roleId;
        this.scopeType = scopeType;
        this.organizationUnitId = organizationUnitId;
        this.workspaceId = workspaceId;
        this.includeDescendants = includeDescendants;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.assignmentStatus = AssignmentStatus.ACTIVE;
        this.assignedBy = assignedBy;
        this.createdAt = validFrom;
    }

    public boolean isEffectiveAt(Instant now) {
        return assignmentStatus == AssignmentStatus.ACTIVE
                && !validFrom.isAfter(now)
                && (validUntil == null || validUntil.isAfter(now));
    }

    public void revoke(UUID actor, Instant now) {
        this.assignmentStatus = AssignmentStatus.REVOKED;
        this.revokedBy = actor;
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public boolean isIncludeDescendants() {
        return includeDescendants;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public AssignmentStatus getAssignmentStatus() {
        return assignmentStatus;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
