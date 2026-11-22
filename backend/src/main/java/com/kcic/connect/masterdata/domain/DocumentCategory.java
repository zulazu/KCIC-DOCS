package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_categories", schema = "master")
public class DocumentCategory {

    @Id
    private UUID id;

    private UUID parentId;

    @Column(nullable = false, length = 60)
    private String categoryCode;

    @Column(nullable = false, length = 140)
    private String categoryName;

    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected DocumentCategory() {
    }

    public DocumentCategory(UUID id, UUID parentId, String categoryCode, String categoryName, String description,
                            UUID actor, Instant now) {
        this.id = id;
        this.parentId = parentId;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.description = description;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(UUID parentId, String categoryName, String description, boolean active, UUID actor,
                       Instant now) {
        this.parentId = parentId;
        this.categoryName = categoryName;
        this.description = description;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDescription() {
        return description;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
