package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_types", schema = "master")
public class DocumentType {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String typeCode;

    @Column(nullable = false, length = 140)
    private String typeName;

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

    protected DocumentType() {
    }

    public DocumentType(UUID id, String typeCode, String typeName, String description, UUID actor, Instant now) {
        this.id = id;
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.description = description;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String typeName, String description, boolean active, UUID actor, Instant now) {
        this.typeName = typeName;
        this.description = description;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getTypeName() {
        return typeName;
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
