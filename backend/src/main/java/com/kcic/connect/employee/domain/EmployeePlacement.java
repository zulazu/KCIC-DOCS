package com.kcic.connect.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_placements", schema = "organization")
public class EmployeePlacement {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID employeeId;

    @Column(nullable = false)
    private UUID divisionUnitId;

    @Column(nullable = false)
    private Instant validFrom;

    private Instant validUntil;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private UUID assignedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected EmployeePlacement() {
    }

    public EmployeePlacement(UUID id, UUID employeeId, UUID divisionUnitId, Instant validFrom, UUID assignedBy) {
        this.id = id;
        this.employeeId = employeeId;
        this.divisionUnitId = divisionUnitId;
        this.validFrom = validFrom;
        this.validUntil = null;
        this.isActive = true;
        this.assignedBy = assignedBy;
        this.createdAt = validFrom;
    }

    public void close(Instant now) {
        this.isActive = false;
        this.validUntil = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getDivisionUnitId() {
        return divisionUnitId;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public Instant getValidUntil() {
        return validUntil;
    }

    public boolean isActive() {
        return isActive;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
