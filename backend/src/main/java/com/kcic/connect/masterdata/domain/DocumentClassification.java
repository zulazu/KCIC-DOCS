package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_classifications", schema = "master")
public class DocumentClassification {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String classificationCode;

    @Column(nullable = false, length = 100)
    private String classificationName;

    @Column(nullable = false)
    private short sensitivityRank;

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

    protected DocumentClassification() {
    }

    public DocumentClassification(UUID id, String classificationCode, String classificationName,
                                  short sensitivityRank, String description, UUID actor, Instant now) {
        this.id = id;
        this.classificationCode = classificationCode;
        this.classificationName = classificationName;
        this.sensitivityRank = sensitivityRank;
        this.description = description;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String classificationName, short sensitivityRank, String description, boolean active,
                       UUID actor, Instant now) {
        this.classificationName = classificationName;
        this.sensitivityRank = sensitivityRank;
        this.description = description;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getClassificationCode() {
        return classificationCode;
    }

    public String getClassificationName() {
        return classificationName;
    }

    public short getSensitivityRank() {
        return sensitivityRank;
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
