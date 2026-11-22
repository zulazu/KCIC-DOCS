package com.kcic.connect.distribution.domain;

import com.kcic.connect.authorization.domain.PermissionCodes;
import java.util.List;

/**
 * Delegation levels are cumulative: preview implies view, download implies preview
 * and view. Every implied permission gets its own ACL grant and provenance row so a
 * revoke removes exactly the access this distribution produced.
 */
public enum DistributionAccessLevel {
    VIEW,
    PREVIEW,
    DOWNLOAD;

    public List<String> impliedPermissionCodes() {
        return switch (this) {
            case VIEW -> List.of(PermissionCodes.DOCUMENT_VIEW);
            case PREVIEW -> List.of(PermissionCodes.DOCUMENT_VIEW, PermissionCodes.DOCUMENT_PREVIEW);
            case DOWNLOAD -> List.of(PermissionCodes.DOCUMENT_VIEW, PermissionCodes.DOCUMENT_PREVIEW,
                    PermissionCodes.DOCUMENT_DOWNLOAD);
        };
    }

    public String itemPermissionCode() {
        return switch (this) {
            case VIEW -> PermissionCodes.DOCUMENT_VIEW;
            case PREVIEW -> PermissionCodes.DOCUMENT_PREVIEW;
            case DOWNLOAD -> PermissionCodes.DOCUMENT_DOWNLOAD;
        };
    }

    public static DistributionAccessLevel fromItemPermission(String permissionCode) {
        return switch (permissionCode) {
            case PermissionCodes.DOCUMENT_PREVIEW -> PREVIEW;
            case PermissionCodes.DOCUMENT_DOWNLOAD -> DOWNLOAD;
            default -> VIEW;
        };
    }
}
