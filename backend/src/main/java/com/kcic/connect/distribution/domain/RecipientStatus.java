package com.kcic.connect.distribution.domain;

import java.util.EnumSet;
import java.util.Set;

public enum RecipientStatus {
    SENT,
    READ,
    REVOKED,
    EXPIRED;

    public boolean isActive() {
        return this == SENT || this == READ;
    }

    public Set<RecipientStatus> activeStatuses() {
        return EnumSet.of(SENT, READ);
    }
}
