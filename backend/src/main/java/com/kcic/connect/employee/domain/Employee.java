package com.kcic.connect.employee.domain;

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
@Table(name = "employees", schema = "identity")
public class Employee {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String nip;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmploymentStatus employmentStatus;

    private UUID positionId;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected Employee() {
    }

    public Employee(UUID id, String nip, String fullName, String email, EmploymentStatus employmentStatus,
                    UUID positionId, UUID actor, Instant now) {
        this.id = id;
        this.nip = nip;
        this.fullName = fullName;
        this.email = email;
        this.employmentStatus = employmentStatus;
        this.positionId = positionId;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actor;
        this.updatedBy = actor;
    }

    public void update(String nip, String fullName, String email, EmploymentStatus employmentStatus,
                       UUID positionId, UUID actor, Instant now) {
        this.nip = nip;
        this.fullName = fullName;
        this.email = email;
        this.employmentStatus = employmentStatus;
        this.positionId = positionId;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getNip() {
        return nip;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public EmploymentStatus getEmploymentStatus() {
        return employmentStatus;
    }

    public UUID getPositionId() {
        return positionId;
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
