package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions", schema = "master")
public class Position {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String positionCode;

    @Column(nullable = false, length = 140)
    private String positionName;

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

    protected Position() {
    }

    public Position(UUID id, String positionCode, String positionName, String description,
                    UUID actor, Instant now) {
        this.id = id;
        this.positionCode = positionCode;
        this.positionName = positionName;
        this.description = description;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String positionName, String description, boolean active, UUID actor, Instant now) {
        this.positionName = positionName;
        this.description = description;
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getPositionCode() {
        return positionCode;
    }

    public String getPositionName() {
        return positionName;
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