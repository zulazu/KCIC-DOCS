package com.kcic.connect.organization.domain;

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
@Table(name = "organization_units", schema = "organization")
public class OrganizationUnit {

    @Id
    private UUID id;

    private UUID parentId;

    @Column(nullable = false, length = 50)
    private String unitCode;

    @Column(nullable = false, length = 160)
    private String unitName;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnitType unitType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitStatus unitStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected OrganizationUnit() {
    }

    public OrganizationUnit(UUID id, UUID parentId, String unitCode, String unitName, String description,
                            UUID actor, Instant now) {
        this(id, parentId, unitCode, unitName, description, null, actor, now);
    }

    public OrganizationUnit(UUID id, UUID parentId, String unitCode, String unitName, String description,
                            UnitType unitType, UUID actor, Instant now) {
        this.id = id;
        this.parentId = parentId;
        this.unitCode = unitCode;
        this.unitName = unitName;
        this.description = description;
        this.unitType = unitType;
        this.unitStatus = UnitStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(UUID parentId, String unitName, String description, UnitType unitType, UnitStatus status,
                       UUID actor, Instant now) {
        this.parentId = parentId;
        this.unitName = unitName;
        this.description = description;
        this.unitType = unitType;
        this.unitStatus = status;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getUnitCode() {
        return unitCode;
    }

    public String getUnitName() {
        return unitName;
    }

    public String getDescription() {
        return description;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public UnitStatus getUnitStatus() {
        return unitStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getRowVersion() {
        return rowVersion;
    }
}
