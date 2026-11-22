package com.kcic.connect.authorization.domain;

/**
 * Stable permission codes seeded in V11. Controllers and services must use
 * these constants instead of magic strings.
 */
public final class PermissionCodes {

    public static final String USER_MANAGE = "user.manage";
    public static final String EMPLOYEE_VIEW = "employee.view";
    public static final String EMPLOYEE_MANAGE = "employee.manage";
    public static final String ROLE_MANAGE = "role.manage";
    public static final String ORGANIZATION_MANAGE = "organization.manage";
    public static final String MASTER_DATA_MANAGE = "master_data.manage";

    public static final String WORKSPACE_VIEW = "workspace.view";
    public static final String WORKSPACE_MANAGE = "workspace.manage";
    public static final String FOLDER_VIEW = "folder.view";
    public static final String FOLDER_CREATE = "folder.create";
    public static final String FOLDER_UPDATE = "folder.update";
    public static final String FOLDER_ARCHIVE = "folder.archive";

    public static final String DOCUMENT_VIEW = "document.view";
    public static final String DOCUMENT_PREVIEW = "document.preview";
    public static final String DOCUMENT_DOWNLOAD = "document.download";
    public static final String DOCUMENT_CREATE = "document.create";
    public static final String DOCUMENT_UPLOAD = "document.upload";
    public static final String DOCUMENT_UPDATE_METADATA = "document.update_metadata";
    public static final String DOCUMENT_CREATE_VERSION = "document.create_version";
    public static final String DOCUMENT_ARCHIVE = "document.archive";
    public static final String DOCUMENT_RESTORE = "document.restore";
    public static final String DOCUMENT_SHARE = "document.share";
    public static final String DOCUMENT_MANAGE_ACCESS = "document.manage_access";

    public static final String REQUEST_CREATE = "request.create";
    public static final String REQUEST_VIEW_OWN = "request.view_own";
    public static final String REQUEST_VIEW_ASSIGNED = "request.view_assigned";
    public static final String REQUEST_APPROVE = "request.approve";
    public static final String REQUEST_REJECT = "request.reject";
    public static final String REQUEST_FULFILL = "request.fulfill";

    public static final String DISTRIBUTION_CREATE = "distribution.create";
    public static final String DISTRIBUTION_VIEW_OWN = "distribution.view_own";
    public static final String DISTRIBUTION_VIEW_RECEIVED = "distribution.view_received";
    public static final String DISTRIBUTION_REVOKE = "distribution.revoke";

    public static final String TEMPLATE_VIEW = "template.view";
    public static final String TEMPLATE_CREATE = "template.create";
    public static final String TEMPLATE_PUBLISH = "template.publish";
    public static final String TEMPLATE_ARCHIVE = "template.archive";

    public static final String NOTIFICATION_VIEW = "notification.view";
    public static final String NOTIFICATION_MANAGE = "notification.manage";
    public static final String AUDIT_VIEW = "audit.view";

    private PermissionCodes() {
    }
}
