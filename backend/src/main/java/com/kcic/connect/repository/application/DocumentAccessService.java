package com.kcic.connect.repository.application;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclDecision;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Single owner of the document access rules. Every list/detail/preview/download/
 * update/version/archive operation goes through here; knowing a document ID is
 * never sufficient.
 *
 * <p>Grant sources are orthogonal: document grants, grants on any folder in the
 * document's folder ancestry, and workspace grants. An explicit deny in any of
 * those scopes wins over every allow, matching ARCHITECTURE §8.2 step 7.</p>
 */
@Service
public class DocumentAccessService {

    public record ActorContext(UUID userId, Set<UUID> roleIds, Set<UUID> unitIds) {
    }

    private final AuthorizationService authorizationService;

    public DocumentAccessService(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public ActorContext context(UUID actor) {
        return new ActorContext(actor, authorizationService.effectiveRoleIds(actor),
                authorizationService.activeUnitIds(actor));
    }

    public boolean canView(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                           List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_VIEW, documentGrants, folderGrants, workspaceGrants,
                true);
    }

    public boolean canPreview(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                              List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_PREVIEW, documentGrants, folderGrants,
                workspaceGrants, true);
    }

    public boolean canDownload(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                               List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_DOWNLOAD, documentGrants, folderGrants,
                workspaceGrants, true);
    }

    public boolean canEdit(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                           List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_UPDATE_METADATA, documentGrants, folderGrants,
                workspaceGrants, false);
    }

    public boolean canUpload(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                             List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_UPLOAD, documentGrants, folderGrants, workspaceGrants,
                false);
    }

    public boolean canCreateVersion(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                                    List<ResourcePermission> folderGrants,
                                    List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_CREATE_VERSION, documentGrants, folderGrants,
                workspaceGrants, false);
    }

    public boolean canArchive(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                              List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_ARCHIVE, documentGrants, folderGrants,
                workspaceGrants, false);
    }

    public boolean canRestore(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                              List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        return decide(ctx, document, PermissionCodes.DOCUMENT_RESTORE, documentGrants, folderGrants,
                workspaceGrants, false);
    }

    /**
     * Sharing requires the role capability itself; an owner relation or ACL allow
     * never invents document.share (ARCHITECTURE §8.3). Explicit deny still wins.
     */
    public boolean canShare(ActorContext ctx, Document document, List<ResourcePermission> documentGrants,
                            List<ResourcePermission> folderGrants, List<ResourcePermission> workspaceGrants) {
        Instant now = Instant.now();
        AclDecision documentDecision = authorizationService.aclDecision(documentGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), PermissionCodes.DOCUMENT_SHARE, now);
        AclDecision folderDecision = authorizationService.aclDecision(folderGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), PermissionCodes.DOCUMENT_SHARE, now);
        AclDecision workspaceDecision = authorizationService.aclDecision(workspaceGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), PermissionCodes.DOCUMENT_SHARE, now);
        if (documentDecision == AclDecision.DENY || folderDecision == AclDecision.DENY
                || workspaceDecision == AclDecision.DENY) {
            return false;
        }
        Set<UUID> ownerUnits = document.getOwnerOrganizationUnitId() == null
                ? Set.of()
                : Set.of(document.getOwnerOrganizationUnitId());
        return authorizationService.allowsResourceCapability(ctx.userId(), PermissionCodes.DOCUMENT_SHARE,
                document.getWorkspaceId(), ownerUnits, true);
    }

    public boolean canManageAccess(ActorContext ctx, Document document) {
        Set<UUID> ownerUnits = document.getOwnerOrganizationUnitId() == null
                ? Set.of()
                : Set.of(document.getOwnerOrganizationUnitId());
        return authorizationService.allowsResourceCapability(ctx.userId(), PermissionCodes.DOCUMENT_MANAGE_ACCESS,
                document.getWorkspaceId(), ownerUnits, true);
    }

    public boolean canCreateInWorkspace(UUID actor, UUID workspaceId, UUID workspaceOwnerUnitId) {
        Set<UUID> ownerUnits = workspaceOwnerUnitId == null ? Set.of() : Set.of(workspaceOwnerUnitId);
        return authorizationService.allowsResourceCapability(actor, PermissionCodes.DOCUMENT_CREATE, workspaceId,
                ownerUnits, true);
    }

    public boolean isOwnerRelation(ActorContext ctx, Document document) {
        if (ctx.userId().equals(document.getOwnerUserId()) || ctx.userId().equals(document.getCreatedBy())) {
            return true;
        }
        return document.getOwnerOrganizationUnitId() != null
                && ctx.unitIds().contains(document.getOwnerOrganizationUnitId());
    }

    /**
     * Content permissions exclude the SYSTEM_ADMIN role (ARCHITECTURE §8.3) but keep
     * other global roles working. Metadata-management permissions keep global scope.
     * Explicit deny in document, folder ancestry, or workspace scope wins; owner
     * relation counts as an ownership grant.
     */
    private boolean decide(ActorContext ctx, Document document, String permissionCode,
                           List<ResourcePermission> documentGrants, List<ResourcePermission> folderGrants,
                           List<ResourcePermission> workspaceGrants, boolean contentPermission) {
        Instant now = Instant.now();
        AclDecision documentDecision = authorizationService.aclDecision(documentGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), permissionCode, now);
        AclDecision folderDecision = authorizationService.aclDecision(folderGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), permissionCode, now);
        AclDecision workspaceDecision = authorizationService.aclDecision(workspaceGrants, ctx.userId(),
                ctx.roleIds(), ctx.unitIds(), permissionCode, now);
        if (documentDecision == AclDecision.DENY || folderDecision == AclDecision.DENY
                || workspaceDecision == AclDecision.DENY) {
            return false;
        }
        if (isOwnerRelation(ctx, document)) {
            return true;
        }
        Set<UUID> ownerUnits = document.getOwnerOrganizationUnitId() == null
                ? Set.of()
                : Set.of(document.getOwnerOrganizationUnitId());
        boolean roleAllows = contentPermission
                ? authorizationService.allowsContentCapability(ctx.userId(), permissionCode,
                        document.getWorkspaceId(), ownerUnits)
                : authorizationService.allowsResourceCapability(ctx.userId(), permissionCode,
                        document.getWorkspaceId(), ownerUnits, true);
        if (roleAllows || documentDecision == AclDecision.ALLOW || folderDecision == AclDecision.ALLOW) {
            return true;
        }
        return workspaceDecision == AclDecision.ALLOW;
    }

    public static void require(boolean allowed, String message) {
        if (!allowed) {
            throw ApiException.forbidden(message);
        }
    }
}
