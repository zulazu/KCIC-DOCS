package com.kcic.connect.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notifications", schema = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(name = "notification_type", nullable = false, length = 60)
    private String notificationType;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false)
    private String message;

    @Column(name = "target_resource_type", length = 40)
    private String targetResourceType;

    @Column(name = "target_resource_id")
    private UUID targetResourceId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "safe_metadata")
    private Map<String, Object> safeMetadata;

    protected Notification() {
    }

    public Notification(UUID id, UUID recipientUserId, String notificationType, String title, String message,
                        String targetResourceType, UUID targetResourceId, Instant createdAt,
                        Map<String, Object> safeMetadata) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.targetResourceType = targetResourceType;
        this.targetResourceId = targetResourceId;
        this.createdAt = createdAt;
        this.safeMetadata = safeMetadata;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientUserId() {
        return recipientUserId;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetResourceType() {
        return targetResourceType;
    }

    public UUID getTargetResourceId() {
        return targetResourceId;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Map<String, Object> getSafeMetadata() {
        return safeMetadata;
    }
}
