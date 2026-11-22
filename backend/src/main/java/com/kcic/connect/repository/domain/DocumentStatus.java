package com.kcic.connect.repository.domain;

public enum DocumentStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED;

    public boolean canTransitionTo(DocumentStatus target) {
        return switch (this) {
            case DRAFT -> target == ACTIVE || target == ARCHIVED;
            case ACTIVE -> target == ARCHIVED;
            case ARCHIVED -> target == ACTIVE;
        };
    }
}
