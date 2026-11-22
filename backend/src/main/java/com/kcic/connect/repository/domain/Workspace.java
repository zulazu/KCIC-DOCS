package com.kcic.connect.repository.domain;

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
@Table(name = "workspaces", schema = "repository")
public class Workspace {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String workspaceCode;

    @Column(nullable = false, length = 160)
    private String workspaceName;

    private String description;

    @Column(nullable = false)
    private UUID ownerOrganizationUnitId;

    private UUID ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceStatus workspaceStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected Workspace() {
    }

    public Workspace(UUID id, String workspaceCode, String workspaceName, String description,
                     UUID ownerOrganizationUnitId, UUID ownerUserId, UUID actor, Instant now) {
        this.id = id;
        this.workspaceCode = workspaceCode;
        this.workspaceName = workspaceName;
        this.description = description;
        this.ownerOrganizationUnitId = ownerOrganizationUnitId;
        this.ownerUserId = ownerUserId;
        this.workspaceStatus = WorkspaceStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String workspaceName, String description, UUID ownerOrganizationUnitId,
                       UUID ownerUserId, WorkspaceStatus status, UUID actor, Instant now) {
        this.workspaceName = workspaceName;
        this.description = description;
        this.ownerOrganizationUnitId = ownerOrganizationUnitId;
        this.ownerUserId = ownerUserId;
        this.workspaceStatus = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getWorkspaceCode() {
        return workspaceCode;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOwnerOrganizationUnitId() {
        return ownerOrganizationUnitId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public WorkspaceStatus getWorkspaceStatus() {
        return workspaceStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
