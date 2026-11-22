package com.kcic.connect.organization.domain;

/**
 * Typed hierarchy: COMPANY → DIRECTORATE → DEPARTMENT → DIVISION.
 * DIVISION is exposed to users as "Biro/Division"; legacy units have no type.
 */
public enum UnitType {
    COMPANY,
    DIRECTORATE,
    DEPARTMENT,
    DIVISION
}
