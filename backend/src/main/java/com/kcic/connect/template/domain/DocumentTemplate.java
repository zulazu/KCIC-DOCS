package com.kcic.connect.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Template metadata points at an existing repository document; binary content and
 * versioning stay in the repository module (SCHEMA §11, ARCHITECTURE §10.5).
 */
@Entity
@Table(name = "document_templates", schema = "template")
public class DocumentTemplate {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID documentId;

    @Column(nullable = false, length = 80, unique = true)
    private String templateCode;

    @Column(nullable = false, length = 180)
    private String templateName;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateStatus templateStatus;

    private UUID currentPublishedVersionId;
    private UUID publishedBy;
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private long rowVersion;

    protected DocumentTemplate() {
    }

    public DocumentTemplate(UUID id, UUID documentId, String templateCode, String templateName,
                            String description, UUID actor, Instant now) {
        this.id = id;
        this.documentId = documentId;
        this.templateCode = templateCode;
        this.templateName = templateName;
        this.description = description;
        this.templateStatus = TemplateStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void updateMetadata(String templateCode, String templateName, String description, UUID actor,
                               Instant now) {
        this.templateCode = templateCode;
        this.templateName = templateName;
        this.description = description;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void publish(UUID documentVersionId, UUID actor, Instant now) {
        this.templateStatus = TemplateStatus.PUBLISHED;
        this.currentPublishedVersionId = documentVersionId;
        this.publishedBy = actor;
        this.publishedAt = now;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void archive(UUID actor, Instant now) {
        this.templateStatus = TemplateStatus.ARCHIVED;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getDescription() {
        return description;
    }

    public TemplateStatus getTemplateStatus() {
        return templateStatus;
    }

    public UUID getCurrentPublishedVersionId() {
        return currentPublishedVersionId;
    }

    public UUID getPublishedBy() {
        return publishedBy;
    }

    public Instant getPublishedAt() {
        return publishedAt;
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

    public long getRowVersion() {
        return rowVersion;
    }
}
