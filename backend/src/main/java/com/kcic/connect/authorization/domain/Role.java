package com.kcic.connect.authorization.domain;

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
@Table(name = "roles", schema = "`authorization`")
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80, unique = true)
    private String roleCode;

    @Column(nullable = false, length = 120)
    private String roleName;

    private String description;

    @Column(nullable = false)
    private boolean isSystemRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleStatus roleStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected Role() {
    }

    public Role(UUID id, String roleCode, String roleName, String description, boolean systemRole, Instant now) {
        this.id = id;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.isSystemRole = systemRole;
        this.roleStatus = RoleStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String roleName, String description, RoleStatus status, UUID actor, Instant now) {
        this.roleName = roleName;
        this.description = description;
        this.roleStatus = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSystemRole() {
        return isSystemRole;
    }

    public RoleStatus getRoleStatus() {
        return roleStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
