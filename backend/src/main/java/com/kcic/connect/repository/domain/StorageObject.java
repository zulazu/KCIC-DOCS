package com.kcic.connect.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "storage_objects", schema = "repository")
public class StorageObject {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String storageProvider;

    @Column(nullable = false, length = 120)
    private String bucketName;

    @Column(nullable = false, length = 700)
    private String objectKey;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 160)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 64)
    private String checksumSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StorageObjectStatus uploadStatus;

    @Column(nullable = false)
    private UUID uploadedBy;

    private Instant uploadedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected StorageObject() {
    }

    public StorageObject(UUID id, String storageProvider, String bucketName, String objectKey,
                         String originalFilename, String contentType, long sizeBytes, String checksumSha256,
                         UUID uploadedBy, Instant now) {
        this.id = id;
        this.storageProvider = storageProvider;
        this.bucketName = bucketName;
        this.objectKey = objectKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.uploadStatus = StorageObjectStatus.AVAILABLE;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = now;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public StorageObjectStatus getUploadStatus() {
        return uploadStatus;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
