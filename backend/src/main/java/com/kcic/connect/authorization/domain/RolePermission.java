package com.kcic.connect.authorization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_permissions", schema = "`authorization`")
@IdClass(RolePermissionId.class)
public class RolePermission {

    @Id
    private UUID roleId;

    @Id
    private UUID permissionId;

    @Column(nullable = false)
    private Instant createdAt;

    protected RolePermission() {
    }

    public RolePermission(UUID roleId, UUID permissionId, Instant createdAt) {
        this.roleId = roleId;
        this.permissionId = permissionId;
        this.createdAt = createdAt;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }
}
