package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tags", schema = "master")
public class Tag {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String tagCode;

    @Column(nullable = false, length = 100)
    private String tagName;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;


    protected Tag() {
    }

    public Tag(UUID id, String tagCode, String tagName, Instant now) {
        this.id = id;
        this.tagCode = tagCode;
        this.tagName = tagName;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String tagName, boolean active, Instant now) {
        this.tagName = tagName;
        this.active = active;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getTagCode() {
        return tagCode;
    }

    public String getTagName() {
        return tagName;
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
