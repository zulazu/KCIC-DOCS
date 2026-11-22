package com.kcic.connect.repository.application;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.infrastructure.PermissionRepository;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.api.ResourceAclDtos.AclSummaryResponse;
import com.kcic.connect.repository.api.ResourceAclDtos.CreateGrantRequest;
import com.kcic.connect.repository.api.ResourceAclDtos.GrantResponse;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.domain.Workspace;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import com.kcic.connect.repository.infrastructure.WorkspaceRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Access management for workspace, folder, and document resources. Authorization
 * itself stays in AuthorizationService; this service validates the target,
 * enforces the managing capability, and maps grants for the UI.
 */
@Service
public class ResourceAclService {

    private static final Set<String> ACL_PERMISSION_CODES = Set.of(
            PermissionCodes.WORKSPACE_VIEW,
            PermissionCodes.FOLDER_VIEW,
            PermissionCodes.DOCUMENT_VIEW,
            PermissionCodes.DOCUMENT_PREVIEW,
            PermissionCodes.DOCUMENT_DOWNLOAD,
            PermissionCodes.DOCUMENT_CREATE,
            PermissionCodes.DOCUMENT_UPLOAD,
            PermissionCodes.DOCUMENT_UPDATE_METADATA,
            PermissionCodes.DOCUMENT_CREATE_VERSION,
            PermissionCodes.DOCUMENT_ARCHIVE,
            PermissionCodes.DOCUMENT_RESTORE,
            PermissionCodes.DOCUMENT_SHARE,
            PermissionCodes.DOCUMENT_MANAGE_ACCESS);

    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final AuthorizationService authorizationService;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final Clock clock;

    public ResourceAclService(WorkspaceRepository workspaceRepository,
                              FolderRepository folderRepository,
                              DocumentRepository documentRepository,
                              AuthorizationService authorizationService,
                              PermissionRepository permissionRepository,
                              UserRepository userRepository,
                              OrganizationUnitRepository organizationUnitRepository,
                              Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.folderRepository = folderRepository;
        this.documentRepository = documentRepository;
        this.authorizationService = authorizationService;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.clock = clock;
    }

    private record Target(ResourceType type, UUID resourceId, UUID workspaceId, Set<UUID> ownerUnitIds) {
    }

    @Transactional(readOnly = true)
    public AclSummaryResponse workspace(UUID actor, UUID workspaceId, boolean includeHistory) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
        Target target = new Target(ResourceType.WORKSPACE, workspaceId, workspaceId,
                ownerUnits(workspace.getOwnerOrganizationUnitId()));
        requireManage(actor, target, PermissionCodes.WORKSPACE_MANAGE);
        return summary(target, includeHistory);
    }

    @Transactional
    public GrantResponse grantWorkspace(UUID actor, UUID workspaceId, CreateGrantRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
        Target target = new Target(ResourceType.WORKSPACE, workspaceId, workspaceId,
                ownerUnits(workspace.getOwnerOrganizationUnitId()));
        requireManage(actor, target, PermissionCodes.WORKSPACE_MANAGE);
        return grant(actor, target, request);
    }

    @Transactional
    public void revokeWorkspace(UUID actor, UUID workspaceId, UUID grantId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
        Target target = new Target(ResourceType.WORKSPACE, workspaceId, workspaceId,
                ownerUnits(workspace.getOwnerOrganizationUnitId()));
        requireManage(actor, target, PermissionCodes.WORKSPACE_MANAGE);
        revoke(actor, target, grantId);
    }

    @Transactional(readOnly = true)
    public AclSummaryResponse folder(UUID actor, UUID workspaceId, UUID folderId, boolean includeHistory) {
        Target target = folderTarget(workspaceId, folderId);
        requireManage(actor, target, PermissionCodes.FOLDER_UPDATE, PermissionCodes.WORKSPACE_MANAGE);
        return summary(target, includeHistory);
    }

    @Transactional
    public GrantResponse grantFolder(UUID actor, UUID workspaceId, UUID folderId, CreateGrantRequest request) {
        Target target = folderTarget(workspaceId, folderId);
        requireManage(actor, target, PermissionCodes.FOLDER_UPDATE, PermissionCodes.WORKSPACE_MANAGE);
        return grant(actor, target, request);
    }

    @Transactional
    public void revokeFolder(UUID actor, UUID workspaceId, UUID folderId, UUID grantId) {
        Target target = folderTarget(workspaceId, folderId);
        requireManage(actor, target, PermissionCodes.FOLDER_UPDATE, PermissionCodes.WORKSPACE_MANAGE);
        revoke(actor, target, grantId);
    }

    @Transactional(readOnly = true)
    public AclSummaryResponse document(UUID actor, UUID documentId, boolean includeHistory) {
        Target target = documentTarget(documentId);
        requireManage(actor, target, PermissionCodes.DOCUMENT_MANAGE_ACCESS);
        return summary(target, includeHistory);
    }

    @Transactional
    public GrantResponse grantDocument(UUID actor, UUID documentId, CreateGrantRequest request) {
        Target target = documentTarget(documentId);
        requireManage(actor, target, PermissionCodes.DOCUMENT_MANAGE_ACCESS);
        return grant(actor, target, request);
    }

    @Transactional
    public void revokeDocument(UUID actor, UUID documentId, UUID grantId) {
        Target target = documentTarget(documentId);
        requireManage(actor, target, PermissionCodes.DOCUMENT_MANAGE_ACCESS);
        revoke(actor, target, grantId);
    }

    private Target folderTarget(UUID workspaceId, UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> ApiException.notFound("Folder tidak ditemukan."));
        if (!folder.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Folder tidak ditemukan.");
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
        return new Target(ResourceType.FOLDER, folderId, workspaceId,
                ownerUnits(workspace.getOwnerOrganizationUnitId()));
    }

    private Target documentTarget(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Dokumen tidak ditemukan."));
        Workspace workspace = workspaceRepository.findById(document.getWorkspaceId())
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
        Set<UUID> units = new HashSet<>(ownerUnits(workspace.getOwnerOrganizationUnitId()));
        if (document.getOwnerOrganizationUnitId() != null) {
            units.add(document.getOwnerOrganizationUnitId());
        }
        return new Target(ResourceType.DOCUMENT, documentId, document.getWorkspaceId(), units);
    }

    private static Set<UUID> ownerUnits(UUID unitId) {
        return unitId == null ? Set.of() : Set.of(unitId);
    }

    private void requireManage(UUID actor, Target target, String... permissionCodes) {
        for (String code : permissionCodes) {
            if (authorizationService.allowsResourceCapability(actor, code, target.workspaceId(),
                    target.ownerUnitIds(), true)) {
                return;
            }
        }
        throw ApiException.forbidden("Anda tidak memiliki izin untuk mengelola akses resource ini.");
    }

    private AclSummaryResponse summary(Target target, boolean includeHistory) {
        List<ResourcePermission> grants = authorizationService.resourcePermissionHistory(target.type(),
                target.resourceId());
        Instant now = Instant.now(clock);
        List<ResourcePermission> active = grants.stream().filter(g -> g.isEffectiveAt(now)).toList();
        List<ResourcePermission> history = includeHistory
                ? grants.stream().filter(g -> !g.isEffectiveAt(now)).toList()
                : List.of();
        NameIndex names = nameIndex(grants);
        return new AclSummaryResponse(target.type().name(), target.resourceId(), true,
                active.stream().map(g -> toResponse(g, names, now)).toList(),
                history.stream().map(g -> toResponse(g, names, now)).toList());
    }

    private GrantResponse grant(UUID actor, Target target, CreateGrantRequest request) {
        String permissionCode = request.permissionCode().trim();
        if (!ACL_PERMISSION_CODES.contains(permissionCode)
                || !permissionAppliesTo(target.type(), permissionCode)) {
            throw ApiException.validation("permissionCode", "Permission tidak dapat diberikan pada resource ini.");
        }
        boolean includeDescendants = Boolean.TRUE.equals(request.includeDescendants());
        if (includeDescendants && request.principalType() != PrincipalType.ORGANIZATION_UNIT) {
            throw ApiException.validation("includeDescendants",
                    "Cakupan turunan hanya berlaku untuk principal unit organisasi.");
        }
        List<ResourcePermission> current = authorizationService.activeResourcePermissions(target.type(),
                target.resourceId());
        boolean duplicate = current.stream().anyMatch(existing -> existing.getEffect() == request.effect()
                && existing.getPermissionId().equals(permissionId(permissionCode))
                && samePrincipal(existing, request));
        if (duplicate) {
            throw ApiException.conflict("Grant serupa sudah aktif untuk resource ini.");
        }
        ResourcePermission saved = authorizationService.grant(actor, target.type(), target.resourceId(),
                request.principalType(), request.principalId(), includeDescendants, permissionCode,
                request.effect(), GrantSource.MANUAL, request.expiresAt());
        return toResponse(saved, nameIndex(List.of(saved)), Instant.now(clock));
    }

    private void revoke(UUID actor, Target target, UUID grantId) {
        ResourcePermission grant = authorizationService.findGrant(grantId);
        if (grant.getResourceType() != target.type() || !grant.getResourceId().equals(target.resourceId())) {
            throw ApiException.notFound("Grant akses tidak ditemukan pada resource ini.");
        }
        authorizationService.revokeGrant(actor, grantId);
    }

    private UUID permissionId(String permissionCode) {
        return permissionRepository.findByPermissionCode(permissionCode)
                .map(Permission::getId)
                .orElseThrow(() -> ApiException.validation("permissionCode", "Permission tidak ditemukan."));
    }

    private static boolean samePrincipal(ResourcePermission grant, CreateGrantRequest request) {
        UUID current = switch (grant.getPrincipalType()) {
            case USER -> grant.getPrincipalUserId();
            case ROLE -> grant.getPrincipalRoleId();
            case ORGANIZATION_UNIT -> grant.getPrincipalOrganizationUnitId();
        };
        return grant.getPrincipalType() == request.principalType() && request.principalId().equals(current);
    }

    private static boolean permissionAppliesTo(ResourceType type, String permissionCode) {
        return switch (type) {
            case DOCUMENT -> permissionCode.startsWith("document.");
            case FOLDER -> permissionCode.startsWith("document.") || permissionCode.startsWith("folder.");
            case WORKSPACE -> true;
        };
    }

    private record NameIndex(Map<UUID, String> users, Map<UUID, String> roles, Map<UUID, String> units,
                             Map<UUID, Permission> permissions) {
    }

    private NameIndex nameIndex(List<ResourcePermission> grants) {
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> roleIds = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        Set<UUID> permissionIds = new HashSet<>();
        for (ResourcePermission grant : grants) {
            if (grant.getPrincipalUserId() != null) {
                userIds.add(grant.getPrincipalUserId());
            }
            if (grant.getPrincipalRoleId() != null) {
                roleIds.add(grant.getPrincipalRoleId());
            }
            if (grant.getPrincipalOrganizationUnitId() != null) {
                unitIds.add(grant.getPrincipalOrganizationUnitId());
            }
            if (grant.getGrantedBy() != null) {
                userIds.add(grant.getGrantedBy());
            }
            if (grant.getRevokedBy() != null) {
                userIds.add(grant.getRevokedBy());
            }
            permissionIds.add(grant.getPermissionId());
        }
        Map<UUID, String> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(u -> users.put(u.getId(), u.getDisplayName()));
        Map<UUID, String> roles = new HashMap<>();
        authorizationService.rolesByIds(roleIds).values()
                .forEach(r -> roles.put(r.getId(), r.getRoleName()));
        Map<UUID, String> units = new HashMap<>();
        organizationUnitRepository.findAllById(unitIds)
                .forEach(u -> units.put(u.getId(), u.getUnitName()));
        Map<UUID, Permission> permissions = new HashMap<>();
        permissionRepository.findAllById(permissionIds)
                .forEach(p -> permissions.put(p.getId(), p));
        return new NameIndex(users, roles, units, permissions);
    }

    private static GrantResponse toResponse(ResourcePermission grant, NameIndex names, Instant now) {
        UUID principalId;
        String principalName;
        switch (grant.getPrincipalType()) {
            case USER -> {
                principalId = grant.getPrincipalUserId();
                principalName = names.users().get(principalId);
            }
            case ROLE -> {
                principalId = grant.getPrincipalRoleId();
                principalName = names.roles().get(principalId);
            }
            default -> {
                principalId = grant.getPrincipalOrganizationUnitId();
                principalName = names.units().get(principalId);
            }
        }
        Permission permission = names.permissions().get(grant.getPermissionId());
        return new GrantResponse(grant.getId(), grant.getResourceType().name(), grant.getResourceId(),
                grant.getPrincipalType().name(), principalId, principalName,
                permission == null ? null : permission.getPermissionCode(),
                permission == null ? null : permission.getPermissionName(),
                grant.getEffect().name(), grant.getGrantSource().name(), grant.isIncludeDescendants(),
                grant.getGrantedBy(),
                names.users().get(grant.getGrantedBy()), grant.getGrantedAt(), grant.getExpiresAt(),
                grant.isEffectiveAt(now), grant.getRevokedBy(), names.users().get(grant.getRevokedBy()),
                grant.getRevokedAt());
    }
}
