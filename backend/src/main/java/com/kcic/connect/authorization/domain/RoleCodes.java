package com.kcic.connect.authorization.domain;

import java.util.List;
import java.util.Set;

/**
 * Stable role codes seeded by V11/V19. The pilot role model (PIC, ADMIN,
 * ADMINISTRATOR, EMPLOYEE) coexists with the baseline codes so existing
 * assignments and audit history remain valid.
 */
public final class RoleCodes {

    public static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";
    public static final String ADMINISTRATOR = "ADMINISTRATOR";
    public static final String DOCUMENT_ADMIN = "DOCUMENT_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String UNIT_MANAGER = "UNIT_MANAGER";
    public static final String PIC = "PIC";
    public static final String EMPLOYEE = "EMPLOYEE";

    /** Governance roles: global scope, but content access still requires ACL/ownership. */
    public static final Set<String> GLOBAL_ADMIN_ROLES = Set.of(SYSTEM_ADMIN, ADMINISTRATOR);

    /** Operational document administrators, consulted in order during request routing. */
    public static final List<String> DOCUMENT_ADMIN_ROUTING = List.of(DOCUMENT_ADMIN, ADMIN);

    /** Unit-level approvers, consulted in order during request routing. */
    public static final List<String> UNIT_APPROVER_ROUTING = List.of(UNIT_MANAGER, PIC);

    private RoleCodes() {
    }
}