package com.kcic.connect.repository.domain;

public enum WorkspaceStatus {
    ACTIVE {
        @Override
        public boolean canTransitionTo(WorkspaceStatus target) {
            return target != null && target != this;
        }
    },
    INACTIVE {
        @Override
        public boolean canTransitionTo(WorkspaceStatus target) {
            return target != null && target != this;
        }
    },
    ARCHIVED {
        @Override
        public boolean canTransitionTo(WorkspaceStatus target) {
            return target == INACTIVE || target == ACTIVE;
        }
    };

    public abstract boolean canTransitionTo(WorkspaceStatus target);
}
