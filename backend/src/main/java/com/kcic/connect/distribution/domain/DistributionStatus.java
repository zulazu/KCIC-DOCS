package com.kcic.connect.distribution.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DistributionStatus {
    DRAFT,
    SENT,
    PARTIALLY_REVOKED,
    REVOKED,
    EXPIRED;

    public boolean isActive() {
        return this == SENT || this == PARTIALLY_REVOKED;
    }

    public Set<DistributionStatus> revocableFrom() {
        return EnumSet.of(SENT, PARTIALLY_REVOKED);
    }
}
