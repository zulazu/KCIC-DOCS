package com.kcic.connect.authorization.domain;

/**
 * Outcome of evaluating explicit resource grants for one principal and capability.
 * NONE means no applicable grant exists, so inherited or role-based policy decides.
 */
public enum AclDecision {
    NONE,
    ALLOW,
    DENY
}
