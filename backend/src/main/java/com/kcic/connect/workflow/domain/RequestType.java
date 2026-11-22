package com.kcic.connect.workflow.domain;

public enum RequestType {
    ACCESS,
    DOCUMENT_DELIVERY,
    TEMPLATE;

    public static RequestType from(String raw) {
        if (raw == null) {
            return null;
        }
        return switch (raw.trim().toUpperCase()) {
            case "ACCESS" -> ACCESS;
            case "DOCUMENT_DELIVERY" -> DOCUMENT_DELIVERY;
            case "TEMPLATE" -> TEMPLATE;
            default -> null;
        };
    }
}
