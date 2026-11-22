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
@Table(name = "folders", schema = "repository")
public class Folder {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID workspaceId;

    private UUID parentFolderId;

    @Column(nullable = false, length = 160)
    private String folderName;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FolderStatus folderStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected Folder() {
    }

    public Folder(UUID id, UUID workspaceId, UUID parentFolderId, String folderName, String description,
                  UUID actor, Instant now) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.parentFolderId = parentFolderId;
        this.folderName = folderName;
        this.description = description;
        this.folderStatus = FolderStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String folderName, String description, UUID parentFolderId, UUID actor, Instant now) {
        this.folderName = folderName;
        this.description = description;
        this.parentFolderId = parentFolderId;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void archive(UUID actor, Instant now) {
        this.folderStatus = FolderStatus.ARCHIVED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getParentFolderId() {
        return parentFolderId;
    }

    public String getFolderName() {
        return folderName;
    }

    public String getDescription() {
        return description;
    }

    public FolderStatus getFolderStatus() {
        return folderStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
