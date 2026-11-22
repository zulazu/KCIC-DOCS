package com.kcic.connect.workflow.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Request lifecycle. The allowed transitions mirror PRD REQ-06 and are enforced
 * server-side; the frontend only mirrors them for affordance.
 */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    IN_PROGRESS,
    FULFILLED,
    CLOSED,
    CANCELLED;

    public boolean isOpen() {
        return this != CLOSED && this != CANCELLED && this != REJECTED;
    }

    public Set<RequestStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(UNDER_REVIEW, APPROVED, REJECTED, CANCELLED);
            case UNDER_REVIEW -> EnumSet.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED -> EnumSet.of(IN_PROGRESS, FULFILLED, CANCELLED);
            case IN_PROGRESS -> EnumSet.of(FULFILLED, CANCELLED);
            case FULFILLED -> EnumSet.of(CLOSED);
            case REJECTED -> EnumSet.of(CLOSED);
            case CLOSED, CANCELLED -> EnumSet.noneOf(RequestStatus.class);
        };
    }

    public boolean canTransitionTo(RequestStatus target) {
        return allowedTransitions().contains(target);
    }
}
