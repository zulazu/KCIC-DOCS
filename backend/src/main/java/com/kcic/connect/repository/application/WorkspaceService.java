package com.kcic.connect.repository.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclDecision;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.api.RepositoryDtos.CreateWorkspaceRequest;
import com.kcic.connect.repository.api.RepositoryDtos.UpdateWorkspaceRequest;
import com.kcic.connect.repository.api.RepositoryDtos.WorkspaceResponse;
import com.kcic.connect.repository.domain.FolderStatus;
import com.kcic.connect.repository.domain.Workspace;
import com.kcic.connect.repository.domain.WorkspaceStatus;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import com.kcic.connect.repository.infrastructure.WorkspaceRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            FolderRepository folderRepository,
                            OrganizationUnitRepository organizationUnitRepository,
                            UserRepository userRepository,
                            AuthorizationService authorizationService,
                            AuditService auditService,
                            Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.folderRepository = folderRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkspaceResponse> list(UUID actor, boolean includeArchived) {
        List<Workspace> candidates = workspaceRepository.findAllByOrderByWorkspaceNameAsc().stream()
                .filter(w -> includeArchived || w.getWorkspaceStatus() != WorkspaceStatus.ARCHIVED)
                .toList();
        if (candidates.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now(clock);
        Set<UUID> roleIds = authorizationService.effectiveRoleIds(actor);
        Set<UUID> unitIds = authorizationService.activeUnitIds(actor);
        Map<UUID, List<ResourcePermission>> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE,
                        candidates.stream().map(Workspace::getId).toList());
        List<Workspace> visible = candidates.stream()
                .filter(workspace -> canView(actor, roleIds, unitIds, workspace,
                        workspaceGrants.getOrDefault(workspace.getId(), List.of()), now))
                .toList();
        return toResponses(actor, visible);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID actor, UUID workspaceId) {
        Workspace workspace = loadWorkspace(workspaceId);
        requireView(actor, workspace);
        return toResponses(actor, List.of(workspace)).get(0);
    }

    @Transactional
    public WorkspaceResponse create(UUID actor, CreateWorkspaceRequest request) {
        String code = request.workspaceCode().trim();
        Set<UUID> ownerUnits = Set.of(request.ownerOrganizationUnitId());
        if (!authorizationService.hasGlobalCapability(actor, PermissionCodes.WORKSPACE_MANAGE)
                && !authorizationService.allowsUnitCapability(actor, PermissionCodes.WORKSPACE_MANAGE, ownerUnits)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk membuat workspace pada unit ini.");
        }
        OrganizationUnit ownerUnit = requireActiveUnit(request.ownerOrganizationUnitId());
        requireExistingOwnerUser(request.ownerUserId());
        if (workspaceRepository.existsByWorkspaceCodeIgnoreCase(code)) {
            throw ApiException.conflict("Kode workspace sudah digunakan.");
        }
        Instant now = Instant.now(clock);
        Workspace workspace = workspaceRepository.save(new Workspace(UUID.randomUUID(), code,
                request.workspaceName().trim(), trimToNull(request.description()), ownerUnit.getId(),
                request.ownerUserId(), actor, now));
        auditService.record(actor, "WORKSPACE_CREATED", "WORKSPACE", workspace.getId(), "SUCCESS",
                Map.of("workspaceCode", workspace.getWorkspaceCode()));
        return toResponses(actor, List.of(workspace)).get(0);
    }

    @Transactional
    public WorkspaceResponse update(UUID actor, UUID workspaceId, UpdateWorkspaceRequest request) {
        Workspace workspace = loadWorkspace(workspaceId);
        requireManage(actor, workspace);
        OrganizationUnit ownerUnit = requireActiveUnit(request.ownerOrganizationUnitId());
        if (!ownerUnit.getId().equals(workspace.getOwnerOrganizationUnitId())
                && !authorizationService.hasGlobalCapability(actor, PermissionCodes.WORKSPACE_MANAGE)
                && !authorizationService.allowsUnitCapability(actor, PermissionCodes.WORKSPACE_MANAGE,
                        Set.of(ownerUnit.getId()))) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk memindahkan workspace ke unit tersebut.");
        }
        requireExistingOwnerUser(request.ownerUserId());
        WorkspaceStatus targetStatus = parseStatus(request.status());
        if (targetStatus != workspace.getWorkspaceStatus()
                && !workspace.getWorkspaceStatus().canTransitionTo(targetStatus)) {
            throw ApiException.conflict("Perpindahan status workspace dari " + workspace.getWorkspaceStatus()
                    + " ke " + targetStatus + " tidak diizinkan.");
        }
        workspace.update(request.workspaceName().trim(), trimToNull(request.description()), ownerUnit.getId(),
                request.ownerUserId(), targetStatus, actor, Instant.now(clock));
        auditService.record(actor, "WORKSPACE_UPDATED", "WORKSPACE", workspaceId, "SUCCESS",
                Map.of("workspaceCode", workspace.getWorkspaceCode(), "status", targetStatus.name()));
        return toResponses(actor, List.of(workspace)).get(0);
    }

    Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> ApiException.notFound("Workspace tidak ditemukan."));
    }

    boolean canView(UUID actor, Workspace workspace) {
        return canView(actor, authorizationService.effectiveRoleIds(actor),
                authorizationService.activeUnitIds(actor), workspace,
                authorizationService.activeResourcePermissions(ResourceType.WORKSPACE, workspace.getId()),
                Instant.now(clock));
    }

    /**
     * Workspace visibility honours resource ACLs: an explicit DENY hides the workspace
     * even when a role scope or global assignment would allow it, while an explicit
     * ALLOW exposes a workspace whose owner unit is outside the actor's scope.
     */
    private boolean canView(UUID actor, Set<UUID> roleIds, Set<UUID> unitIds, Workspace workspace,
                            List<ResourcePermission> workspaceGrants, Instant now) {
        AclDecision decision = authorizationService.aclDecision(workspaceGrants, actor, roleIds, unitIds,
                PermissionCodes.WORKSPACE_VIEW, now);
        if (decision == AclDecision.DENY) {
            return false;
        }
        if (decision == AclDecision.ALLOW) {
            return true;
        }
        return authorizationService.allowsResourceCapability(actor, PermissionCodes.WORKSPACE_VIEW,
                workspace.getId(), Set.of(workspace.getOwnerOrganizationUnitId()), true);
    }

    void requireView(UUID actor, Workspace workspace) {
        if (!canView(actor, workspace)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat workspace ini.");
        }
    }

    boolean canManage(UUID actor, Workspace workspace) {
        return authorizationService.allowsResourceCapability(actor, PermissionCodes.WORKSPACE_MANAGE,
                workspace.getId(), Set.of(workspace.getOwnerOrganizationUnitId()), true);
    }

    void requireManage(UUID actor, Workspace workspace) {
        if (!canManage(actor, workspace)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk mengelola workspace ini.");
        }
    }

    WorkspaceResponse toResponse(Workspace workspace, String ownerUnitName, long activeFolderCount,
                                 boolean canManage) {
        return new WorkspaceResponse(workspace.getId(), workspace.getWorkspaceCode(),
                workspace.getWorkspaceName(), workspace.getDescription(),
                workspace.getOwnerOrganizationUnitId(), ownerUnitName, workspace.getOwnerUserId(),
                workspace.getWorkspaceStatus().name(), activeFolderCount, canManage,
                workspace.getCreatedAt(), workspace.getUpdatedAt());
    }

    private List<WorkspaceResponse> toResponses(UUID actor, List<Workspace> workspaces) {
        if (workspaces.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = workspaces.stream().map(Workspace::getId).toList();
        Map<UUID, Long> folderCounts = folderRepository.findAllByWorkspaceIdIn(ids).stream()
                .filter(folder -> folder.getFolderStatus() == FolderStatus.ACTIVE)
                .collect(Collectors.groupingBy(folder -> folder.getWorkspaceId(), Collectors.counting()));
        Map<UUID, String> unitNames = organizationUnitRepository.findAllById(
                        workspaces.stream().map(Workspace::getOwnerOrganizationUnitId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(OrganizationUnit::getId, OrganizationUnit::getUnitName));
        return workspaces.stream()
                .map(workspace -> toResponse(workspace, unitNames.get(workspace.getOwnerOrganizationUnitId()),
                        folderCounts.getOrDefault(workspace.getId(), 0L), canManage(actor, workspace)))
                .toList();
    }

    private OrganizationUnit requireActiveUnit(UUID unitId) {
        OrganizationUnit unit = organizationUnitRepository.findById(unitId)
                .orElseThrow(() -> ApiException.validation("ownerOrganizationUnitId", "Unit tidak ditemukan."));
        if (unit.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("ownerOrganizationUnitId", "Unit pemilik harus aktif.");
        }
        return unit;
    }

    private void requireExistingOwnerUser(UUID ownerUserId) {
        if (ownerUserId != null && !userRepository.existsById(ownerUserId)) {
            throw ApiException.validation("ownerUserId", "Pemilik workspace tidak ditemukan.");
        }
    }

    private static WorkspaceStatus parseStatus(String status) {
        try {
            return WorkspaceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "Status workspace tidak dikenal.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
