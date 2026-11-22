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
@Table(name = "resource_permissions", schema = "`authorization`")
public class ResourcePermission {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PrincipalType principalType;

    private UUID principalUserId;
    private UUID principalRoleId;
    private UUID principalOrganizationUnitId;

    @Column(nullable = false)
    private boolean includeDescendants;

    @Column(nullable = false)
    private UUID permissionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AclEffect effect;

    @Column(nullable = false)
    private UUID grantedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GrantSource grantSource;

    @Column(nullable = false)
    private Instant grantedAt;

    private Instant expiresAt;
    private UUID revokedBy;
    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected ResourcePermission() {
    }

    public ResourcePermission(UUID id, ResourceType resourceType, UUID resourceId, PrincipalType principalType,
                              UUID principalUserId, UUID principalRoleId, UUID principalOrganizationUnitId,
                              boolean includeDescendants, UUID permissionId, AclEffect effect, UUID grantedBy,
                              GrantSource grantSource, Instant grantedAt, Instant expiresAt) {
        this.id = id;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.principalType = principalType;
        this.principalUserId = principalUserId;
        this.principalRoleId = principalRoleId;
        this.principalOrganizationUnitId = principalOrganizationUnitId;
        this.includeDescendants = includeDescendants;
        this.permissionId = permissionId;
        this.effect = effect;
        this.grantedBy = grantedBy;
        this.grantSource = grantSource;
        this.grantedAt = grantedAt;
        this.expiresAt = expiresAt;
        this.createdAt = grantedAt;
    }

    public boolean isEffectiveAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke(UUID actor, Instant now) {
        this.revokedBy = actor;
        this.revokedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public PrincipalType getPrincipalType() {
        return principalType;
    }

    public UUID getPrincipalUserId() {
        return principalUserId;
    }

    public UUID getPrincipalRoleId() {
        return principalRoleId;
    }

    public UUID getPrincipalOrganizationUnitId() {
        return principalOrganizationUnitId;
    }

    public boolean isIncludeDescendants() {
        return includeDescendants;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public AclEffect getEffect() {
        return effect;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }

    public GrantSource getGrantSource() {
        return grantSource;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getRevokedBy() {
        return revokedBy;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
