package com.kcic.connect.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "document_requests", schema = "workflow")
public class DocumentRequest {

    @Id
    private UUID id;

    @Column(name = "request_code", nullable = false, length = 50)
    private String requestCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private RequestType requestType;

    @Column(name = "requester_user_id", nullable = false)
    private UUID requesterUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Column(name = "target_document_id")
    private UUID targetDocumentId;

    @Column(name = "target_owner_user_id")
    private UUID targetOwnerUserId;

    @Column(name = "target_organization_unit_id")
    private UUID targetOrganizationUnitId;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "needed_by_date")
    private LocalDate neededByDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 30)
    private RequestStatus requestStatus;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected DocumentRequest() {
    }

    public DocumentRequest(UUID id, String requestCode, RequestType requestType, UUID requesterUserId,
                           String title, String description, String reason, UUID targetDocumentId,
                           UUID targetOwnerUserId, UUID targetOrganizationUnitId, UUID assignedToUserId,
                           LocalDate neededByDate, RequestStatus requestStatus, Instant submittedAt,
                           Instant createdAt) {
        this.id = id;
        this.requestCode = requestCode;
        this.requestType = requestType;
        this.requesterUserId = requesterUserId;
        this.title = title;
        this.description = description;
        this.reason = reason;
        this.targetDocumentId = targetDocumentId;
        this.targetOwnerUserId = targetOwnerUserId;
        this.targetOrganizationUnitId = targetOrganizationUnitId;
        this.assignedToUserId = assignedToUserId;
        this.neededByDate = neededByDate;
        this.requestStatus = requestStatus;
        this.submittedAt = submittedAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void transitionTo(RequestStatus target, Instant now) {
        if (!requestStatus.canTransitionTo(target)) {
            throw new IllegalStateException("Transisi status tidak valid: " + requestStatus + " -> " + target);
        }
        requestStatus = target;
        updatedAt = now;
        if (target == RequestStatus.SUBMITTED && submittedAt == null) {
            submittedAt = now;
        }
        if (target == RequestStatus.CLOSED) {
            closedAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getRequestCode() {
        return requestCode;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public UUID getRequesterUserId() {
        return requesterUserId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getReason() {
        return reason;
    }

    public UUID getTargetDocumentId() {
        return targetDocumentId;
    }

    public UUID getTargetOwnerUserId() {
        return targetOwnerUserId;
    }

    public UUID getTargetOrganizationUnitId() {
        return targetOrganizationUnitId;
    }

    public UUID getAssignedToUserId() {
        return assignedToUserId;
    }

    public LocalDate getNeededByDate() {
        return neededByDate;
    }

    public RequestStatus getRequestStatus() {
        return requestStatus;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
