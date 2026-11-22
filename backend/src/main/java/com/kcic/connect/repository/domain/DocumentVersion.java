package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions", schema = "repository")
public class DocumentVersion {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int versionNumber;

    @Column(length = 60)
    private String versionLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentSource contentSource;

    private UUID storageObjectId;
    private UUID externalResourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VersionStatus versionStatus;

    private String changeSummary;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant publishedAt;

    protected DocumentVersion() {
    }

    public DocumentVersion(UUID id, UUID documentId, int versionNumber, String versionLabel,
                           UUID storageObjectId, UUID externalResourceId, String changeSummary,
                           UUID actor, Instant now) {
        this.id = id;
        this.documentId = documentId;
        this.versionNumber = versionNumber;
        this.versionLabel = versionLabel;
        this.storageObjectId = storageObjectId;
        this.externalResourceId = externalResourceId;
        this.contentSource = storageObjectId != null ? ContentSource.STORAGE_OBJECT : ContentSource.EXTERNAL_LINK;
        this.versionStatus = VersionStatus.CURRENT;
        this.changeSummary = changeSummary;
        this.createdBy = actor;
        this.createdAt = now;
        this.publishedAt = now;
    }

    public void supersede() {
        if (this.versionStatus == VersionStatus.CURRENT) {
            this.versionStatus = VersionStatus.SUPERSEDED;
        }
    }

    public void promote(Instant now) {
        this.versionStatus = VersionStatus.CURRENT;
        this.publishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public ContentSource getContentSource() {
        return contentSource;
    }

    public UUID getStorageObjectId() {
        return storageObjectId;
    }

    public UUID getExternalResourceId() {
        return externalResourceId;
    }

    public VersionStatus getVersionStatus() {
        return versionStatus;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
