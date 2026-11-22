package com.kcic.connect.identity.domain;

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
@Table(name = "users", schema = "identity")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus accountStatus;

    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private int failedLoginCount;

    private UUID employeeId;

    private Instant lockedUntil;
    private Instant passwordChangedAt;
    private Instant lastLoginAt;
    private Instant deactivatedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    @Version
    @Column(nullable = false)
    private Long rowVersion;

    protected User() {
    }

    public User(UUID id, String username, String email, String displayName, String passwordHash,
                boolean mustChangePassword, Instant now, UUID createdBy) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.accountStatus = AccountStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = createdBy;
        this.updatedBy = createdBy;
    }

    public void updateProfile(String displayName, String email, UUID actor, Instant now) {
        this.displayName = displayName;
        this.email = email;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void linkEmployee(UUID employeeId, UUID actor, Instant now) {
        this.employeeId = employeeId;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        this.updatedAt = now;
        if (this.accountStatus == AccountStatus.LOCKED) {
            this.accountStatus = AccountStatus.ACTIVE;
        }
    }

    public void recordFailedLogin(int maxFailures, int lockMinutes, Instant now) {
        this.failedLoginCount += 1;
        if (this.failedLoginCount >= maxFailures) {
            this.accountStatus = AccountStatus.LOCKED;
            this.lockedUntil = now.plusSeconds(lockMinutes * 60L);
        }
        this.updatedAt = now;
    }

    public boolean isLockExpired(Instant now) {
        return accountStatus == AccountStatus.LOCKED
                && lockedUntil != null
                && lockedUntil.isBefore(now);
    }

    public void unlock(Instant now) {
        this.accountStatus = AccountStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginCount = 0;
        this.updatedAt = now;
    }

    public void changePassword(String newHash, boolean mustChange, UUID actor, Instant now) {
        this.passwordHash = newHash;
        this.mustChangePassword = mustChange;
        this.passwordChangedAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        if (this.accountStatus == AccountStatus.LOCKED) {
            this.accountStatus = AccountStatus.ACTIVE;
        }
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public void changeStatus(AccountStatus status, UUID actor, Instant now) {
        this.accountStatus = status;
        this.deactivatedAt = status == AccountStatus.INACTIVE ? now : null;
        if (status == AccountStatus.ACTIVE) {
            this.lockedUntil = null;
            this.failedLoginCount = 0;
        }
        if (status == AccountStatus.LOCKED && this.lockedUntil == null) {
            this.lockedUntil = now.plusSeconds(15 * 60L);
        }
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
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
