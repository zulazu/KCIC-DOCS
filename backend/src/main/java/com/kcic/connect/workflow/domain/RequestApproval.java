package com.kcic.connect.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_approvals", schema = "workflow")
public class RequestApproval {

    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "approver_user_id", nullable = false)
    private UUID approverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RequestApproval() {
    }

    public RequestApproval(UUID id, UUID requestId, UUID approverUserId, Instant now) {
        this.id = id;
        this.requestId = requestId;
        this.approverUserId = approverUserId;
        this.decision = ApprovalDecision.PENDING;
        this.assignedAt = now;
        this.createdAt = now;
    }

    public void decide(ApprovalDecision decision, String reason, Instant now) {
        this.decision = decision;
        this.decisionReason = reason;
        this.decidedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getApproverUserId() {
        return approverUserId;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
