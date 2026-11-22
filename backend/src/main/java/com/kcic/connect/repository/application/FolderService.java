package com.kcic.connect.repository.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclDecision;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.repository.api.RepositoryDtos.CreateFolderRequest;
import com.kcic.connect.repository.api.RepositoryDtos.FolderResponse;
import com.kcic.connect.repository.api.RepositoryDtos.UpdateFolderRequest;
import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.domain.FolderStatus;
import com.kcic.connect.repository.domain.Workspace;
import com.kcic.connect.repository.domain.WorkspaceStatus;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final WorkspaceService workspaceService;
    private final FolderGrantResolver folderGrantResolver;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public FolderService(FolderRepository folderRepository,
                         WorkspaceService workspaceService,
                         FolderGrantResolver folderGrantResolver,
                         AuthorizationService authorizationService,
                         AuditService auditService,
                         Clock clock) {
        this.folderRepository = folderRepository;
        this.workspaceService = workspaceService;
        this.folderGrantResolver = folderGrantResolver;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FolderResponse> list(UUID actor, UUID workspaceId, boolean includeArchived) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        workspaceService.requireView(actor, workspace);
        List<Folder> folders = includeArchived
                ? folderRepository.findAllByWorkspaceIdOrderByFolderNameAsc(workspaceId)
                : folderRepository.findAllByWorkspaceIdAndFolderStatusOrderByFolderNameAsc(workspaceId,
                        FolderStatus.ACTIVE);
        if (folders.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        Set<UUID> roleIds = authorizationService.effectiveRoleIds(actor);
        Set<UUID> unitIds = authorizationService.activeUnitIds(actor);
        Map<UUID, List<ResourcePermission>> ancestryGrants = folderGrantResolver
                .grantsFor(folders.stream().map(Folder::getId).toList());
        List<ResourcePermission> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE, workspaceId);
        boolean roleScopedView = authorizationService.allowsResourceCapability(actor, PermissionCodes.FOLDER_VIEW,
                workspaceId, Set.of(workspace.getOwnerOrganizationUnitId()), true);
        boolean canManage = canManageFolders(actor, workspace);
        return folders.stream()
                .filter(folder -> canViewFolder(actor, roleIds, unitIds, roleScopedView, folder,
                        ancestryGrants.getOrDefault(folder.getId(), List.of()), workspaceGrants, now))
                .map(folder -> toResponse(folder, canManage))
                .toList();
    }

    @Transactional
    public FolderResponse create(UUID actor, UUID workspaceId, CreateFolderRequest request) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        requireCapability(actor, PermissionCodes.FOLDER_CREATE, workspace, "membuat folder di workspace ini");
        requireWorkspaceActive(workspace);
        UUID parentFolderId = request.parentFolderId();
        if (parentFolderId != null) {
            requireActiveFolderInWorkspace(workspaceId, parentFolderId);
        }
        String name = request.folderName().trim();
        requireUniqueSiblingName(workspaceId, parentFolderId, name, null);
        Instant now = Instant.now(clock);
        Folder folder = folderRepository.save(new Folder(UUID.randomUUID(), workspaceId, parentFolderId, name,
                trimToNull(request.description()), actor, now));
        auditService.record(actor, "FOLDER_CREATED", "FOLDER", folder.getId(), "SUCCESS",
                Map.of("workspaceId", workspaceId.toString(), "folderName", name));
        return toResponse(folder, canManageFolders(actor, workspace));
    }

    @Transactional
    public FolderResponse update(UUID actor, UUID workspaceId, UUID folderId, UpdateFolderRequest request) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        requireCapability(actor, PermissionCodes.FOLDER_UPDATE, workspace, "mengubah folder workspace ini");
        if (workspace.getWorkspaceStatus() == WorkspaceStatus.ARCHIVED) {
            throw ApiException.conflict("Workspace yang diarsipkan tidak dapat diubah.");
        }
        Folder folder = requireActiveFolderInWorkspace(workspaceId, folderId);
        UUID parentFolderId = request.parentFolderId();
        validateMove(workspaceId, folderId, parentFolderId);
        String name = request.folderName().trim();
        requireUniqueSiblingName(workspaceId, parentFolderId, name, folderId);
        folder.update(name, trimToNull(request.description()), parentFolderId, actor, Instant.now(clock));
        auditService.record(actor, "FOLDER_UPDATED", "FOLDER", folderId, "SUCCESS",
                Map.of("workspaceId", workspaceId.toString(), "folderName", name,
                        "moved", parentFolderId == null ? "ROOT" : parentFolderId.toString()));
        return toResponse(folder, canManageFolders(actor, workspace));
    }

    @Transactional
    public FolderResponse archive(UUID actor, UUID workspaceId, UUID folderId) {
        Workspace workspace = workspaceService.loadWorkspace(workspaceId);
        requireCapability(actor, PermissionCodes.FOLDER_ARCHIVE, workspace, "mengarsipkan folder workspace ini");
        if (workspace.getWorkspaceStatus() == WorkspaceStatus.ARCHIVED) {
            throw ApiException.conflict("Workspace yang diarsipkan tidak dapat diubah.");
        }
        Folder folder = requireActiveFolderInWorkspace(workspaceId, folderId);
        if (folderRepository.existsByParentFolderIdAndFolderStatus(folderId, FolderStatus.ACTIVE)) {
            throw ApiException.conflict("Folder masih memiliki sub-folder aktif. Arsipkan sub-folder terlebih dahulu.");
        }
        folder.archive(actor, Instant.now(clock));
        auditService.record(actor, "FOLDER_ARCHIVED", "FOLDER", folderId, "SUCCESS",
                Map.of("workspaceId", workspaceId.toString(), "folderName", folder.getFolderName()));
        return toResponse(folder, canManageFolders(actor, workspace));
    }

    private void validateMove(UUID workspaceId, UUID folderId, UUID parentFolderId) {
        if (parentFolderId == null) {
            return;
        }
        if (parentFolderId.equals(folderId)) {
            throw ApiException.validation("parentFolderId", "Folder tidak boleh menjadi induk dirinya sendiri.");
        }
        requireActiveFolderInWorkspace(workspaceId, parentFolderId);
        Set<UUID> seen = new HashSet<>();
        UUID cursor = parentFolderId;
        while (cursor != null) {
            if (cursor.equals(folderId)) {
                throw ApiException.validation("parentFolderId", "Folder tidak boleh dipindahkan ke bawah dirinya sendiri.");
            }
            if (!seen.add(cursor) || seen.size() > 200) {
                throw ApiException.validation("parentFolderId", "Struktur folder tidak valid.");
            }
            Folder current = folderRepository.findById(cursor)
                    .orElseThrow(() -> ApiException.validation("parentFolderId", "Folder induk tidak ditemukan."));
            cursor = current.getParentFolderId();
        }
    }

    private Folder requireActiveFolderInWorkspace(UUID workspaceId, UUID folderId) {
        Folder folder = folderRepository.findById(folderId)
                .orElseThrow(() -> ApiException.notFound("Folder tidak ditemukan."));
        if (!folder.getWorkspaceId().equals(workspaceId)) {
            throw ApiException.notFound("Folder tidak ditemukan.");
        }
        if (folder.getFolderStatus() != FolderStatus.ACTIVE) {
            throw ApiException.conflict("Folder tidak aktif.");
        }
        return folder;
    }

    private void requireUniqueSiblingName(UUID workspaceId, UUID parentFolderId, String folderName, UUID excludeId) {
        boolean exists = parentFolderId == null
                ? (excludeId == null
                    ? folderRepository.existsActiveRootFolderName(workspaceId, folderName, FolderStatus.ACTIVE)
                    : folderRepository.existsActiveRootFolderNameExcluding(workspaceId, folderName,
                            FolderStatus.ACTIVE, excludeId))
                : (excludeId == null
                    ? folderRepository.existsActiveChildFolderName(workspaceId, parentFolderId, folderName,
                            FolderStatus.ACTIVE)
                    : folderRepository.existsActiveChildFolderNameExcluding(workspaceId, parentFolderId, folderName,
                            FolderStatus.ACTIVE, excludeId));
        if (exists) {
            throw ApiException.validation("folderName", "Nama folder sudah digunakan pada tingkat yang sama.");
        }
    }

    private void requireWorkspaceActive(Workspace workspace) {
        if (workspace.getWorkspaceStatus() != WorkspaceStatus.ACTIVE) {
            throw ApiException.conflict("Workspace harus berstatus ACTIVE untuk menambah folder.");
        }
    }

    private void requireCapability(UUID actor, String permissionCode, Workspace workspace, String action) {
        if (!authorizationService.allowsResourceCapability(actor, permissionCode, workspace.getId(),
                Set.of(workspace.getOwnerOrganizationUnitId()), true)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk " + action + ".");
        }
    }

    private boolean canManageFolders(UUID actor, Workspace workspace) {
        return authorizationService.allowsResourceCapability(actor, PermissionCodes.FOLDER_CREATE,
                workspace.getId(), Set.of(workspace.getOwnerOrganizationUnitId()), true)
                || authorizationService.allowsResourceCapability(actor, PermissionCodes.FOLDER_UPDATE,
                workspace.getId(), Set.of(workspace.getOwnerOrganizationUnitId()), true)
                || authorizationService.allowsResourceCapability(actor, PermissionCodes.FOLDER_ARCHIVE,
                workspace.getId(), Set.of(workspace.getOwnerOrganizationUnitId()), true);
    }

    /**
     * Folder visibility resolves the folder ACL, every ancestor folder ACL, and the
     * workspace ACL: an explicit DENY anywhere hides the folder, an explicit ALLOW
     * exposes it outside the actor's unit scope, otherwise the role scope applies.
     */
    private boolean canViewFolder(UUID actor, Set<UUID> roleIds, Set<UUID> unitIds, boolean roleScopedView,
                                  Folder folder, List<ResourcePermission> folderGrants,
                                  List<ResourcePermission> workspaceGrants, Instant now) {
        AclDecision folderDecision = authorizationService.aclDecision(folderGrants, actor, roleIds, unitIds,
                PermissionCodes.FOLDER_VIEW, now);
        AclDecision workspaceDecision = authorizationService.aclDecision(workspaceGrants, actor, roleIds, unitIds,
                PermissionCodes.FOLDER_VIEW, now);
        if (folderDecision == AclDecision.DENY || workspaceDecision == AclDecision.DENY) {
            return false;
        }
        return roleScopedView || folderDecision == AclDecision.ALLOW || workspaceDecision == AclDecision.ALLOW;
    }

    private static FolderResponse toResponse(Folder folder, boolean canManage) {
        return new FolderResponse(folder.getId(), folder.getWorkspaceId(), folder.getParentFolderId(),
                folder.getFolderName(), folder.getDescription(), folder.getFolderStatus().name(), canManage,
                folder.getCreatedAt(), folder.getUpdatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
