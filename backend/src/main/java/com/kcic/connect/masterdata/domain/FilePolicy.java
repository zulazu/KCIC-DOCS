package com.kcic.connect.masterdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "file_policies", schema = "master")
public class FilePolicy {

    @Id
    private UUID id;

    @Column(nullable = false, length = 60)
    private String policyCode;

    @Column(nullable = false, length = 120)
    private String policyName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] allowedExtensions;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] allowedMimeTypes;

    @Column(nullable = false)
    private long maxFileSizeBytes;

    @Column(name = "is_default", nullable = false)
    private boolean defaultPolicy;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private UUID updatedBy;


    protected FilePolicy() {
    }

    public void update(List<String> extensions, List<String> mimeTypes, long maxFileSizeBytes, UUID actor,
                       Instant now) {
        this.allowedExtensions = extensions.toArray(String[]::new);
        this.allowedMimeTypes = mimeTypes.toArray(String[]::new);
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.updatedBy = actor;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getPolicyCode() {
        return policyCode;
    }

    public String getPolicyName() {
        return policyName;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions == null ? List.of() : List.copyOf(Arrays.asList(allowedExtensions));
    }

    public List<String> getAllowedMimeTypes() {
        return allowedMimeTypes == null ? List.of() : List.copyOf(Arrays.asList(allowedMimeTypes));
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public boolean isDefaultPolicy() {
        return defaultPolicy;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
