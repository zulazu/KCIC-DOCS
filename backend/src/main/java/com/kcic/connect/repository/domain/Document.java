package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "documents", schema = "repository")
public class Document {

    @Id
    private UUID id;

    @Column(length = 100)
    private String documentCode;

    @Column(nullable = false, length = 240)
    private String title;

    private String description;

    @Column(nullable = false)
    private UUID workspaceId;

    private UUID folderId;

    @Column(nullable = false)
    private UUID categoryId;

    @Column(nullable = false)
    private UUID documentTypeId;

    @Column(nullable = false)
    private UUID classificationId;

    private UUID ownerUserId;
    private UUID ownerOrganizationUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentStatus documentStatus;

    private LocalDate effectiveDate;
    private LocalDate reviewDate;
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private UUID createdBy;

    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected Document() {
    }

    public Document(UUID id, String documentCode, String title, String description, UUID workspaceId,
                    UUID folderId, UUID categoryId, UUID documentTypeId, UUID classificationId,
                    UUID ownerUserId, UUID ownerOrganizationUnitId, UUID actor, Instant now) {
        this.id = id;
        this.documentCode = documentCode;
        this.title = title;
        this.description = description;
        this.workspaceId = workspaceId;
        this.folderId = folderId;
        this.categoryId = categoryId;
        this.documentTypeId = documentTypeId;
        this.classificationId = classificationId;
        this.ownerUserId = ownerUserId;
        this.ownerOrganizationUnitId = ownerOrganizationUnitId;
        this.documentStatus = DocumentStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void updateMetadata(String documentCode, String title, String description, UUID folderId,
                               UUID categoryId, UUID documentTypeId, UUID classificationId,
                               UUID ownerUserId, UUID ownerOrganizationUnitId, LocalDate effectiveDate,
                               LocalDate reviewDate, LocalDate expiryDate, UUID actor, Instant now) {
        this.documentCode = documentCode;
        this.title = title;
        this.description = description;
        this.folderId = folderId;
        this.categoryId = categoryId;
        this.documentTypeId = documentTypeId;
        this.classificationId = classificationId;
        this.ownerUserId = ownerUserId;
        this.ownerOrganizationUnitId = ownerOrganizationUnitId;
        this.effectiveDate = effectiveDate;
        this.reviewDate = reviewDate;
        this.expiryDate = expiryDate;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void changeStatus(DocumentStatus target, UUID actor, Instant now) {
        this.documentStatus = target;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void touch(UUID actor, Instant now) {
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getDocumentCode() {
        return documentCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getFolderId() {
        return folderId;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public UUID getDocumentTypeId() {
        return documentTypeId;
    }

    public UUID getClassificationId() {
        return classificationId;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getOwnerOrganizationUnitId() {
        return ownerOrganizationUnitId;
    }

    public DocumentStatus getDocumentStatus() {
        return documentStatus;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
