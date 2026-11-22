package com.kcic.connect.authorization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions", schema = "`authorization`")
public class Permission {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120, unique = true)
    private String permissionCode;

    @Column(nullable = false, length = 160)
    private String permissionName;

    private String description;

    @Column(nullable = false, length = 60)
    private String moduleCode;

    @Column(nullable = false)
    private Instant createdAt;

    protected Permission() {
    }

    public UUID getId() {
        return id;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getPermissionName() {
        return permissionName;
    }

    public String getDescription() {
        return description;
    }

    public String getModuleCode() {
        return moduleCode;
    }
}
