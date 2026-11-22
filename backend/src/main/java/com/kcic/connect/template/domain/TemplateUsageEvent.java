package com.kcic.connect.template.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "template_usage_events", schema = "template")
public class TemplateUsageEvent {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID templateId;

    @Column(nullable = false)
    private UUID documentVersionId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TemplateUsageType usageType;

    @Column(nullable = false)
    private Instant occurredAt;

    protected TemplateUsageEvent() {
    }

    public TemplateUsageEvent(UUID id, UUID templateId, UUID documentVersionId, UUID userId,
                              TemplateUsageType usageType, Instant occurredAt) {
        this.id = id;
        this.templateId = templateId;
        this.documentVersionId = documentVersionId;
        this.userId = userId;
        this.usageType = usageType;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTemplateId() {
        return templateId;
    }

    public UUID getDocumentVersionId() {
        return documentVersionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public TemplateUsageType getUsageType() {
        return usageType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
