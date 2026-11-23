package com.kcic.connect.authorization.application;

import com.kcic.connect.authorization.api.RoleDtos.AssignRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.CreateRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.PermissionResponse;
import com.kcic.connect.authorization.api.RoleDtos.RoleHolderView;
import com.kcic.connect.authorization.api.RoleDtos.RoleResponse;
import com.kcic.connect.authorization.api.RoleDtos.UpdateRoleRequest;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.RoleStatus;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.authorization.infrastructure.UserRoleAssignmentRepository;
import com.kcic.connect.identity.api.UserDtos.RoleAssignmentView;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAdminService {

    private static final java.util.Set<String> PROTECTED_GOVERNANCE_ROLES =
            com.kcic.connect.authorization.domain.RoleCodes.GLOBAL_ADMIN_ROLES;

    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final WorkspaceLookup workspaceLookup;

    public RoleAdminService(AuthorizationService authorizationService,
                            UserRepository userRepository,
                            UserRoleAssignmentRepository assignmentRepository,
                            OrganizationUnitRepository organizationUnitRepository,
                            WorkspaceLookup workspaceLookup) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.workspaceLookup = workspaceLookup;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> roles(UUID actor) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        List<Role> roles = authorizationService.listRoles();
        Map<UUID, Set<String>> permissionMap = authorizationService.permissionCodesByRole(
                roles.stream().map(Role::getId).toList());
        List<RoleResponse> result = new ArrayList<>();
        for (Role role : roles) {
            List<String> codes = permissionMap.getOrDefault(role.getId(), Set.of()).stream().sorted().toList();
            result.add(new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(),
                    role.getDescription(), role.isSystemRole(), role.getRoleStatus().name(), codes));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> permissions(UUID actor) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        return authorizationService.listPermissions().stream()
                .map(this::toPermission)
                .toList();
    }

    @Transactional
    public RoleResponse createRole(UUID actor, CreateRoleRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        Role role = authorizationService.createRole(actor, request.roleCode(), request.roleName().trim(),
                request.description());
        return toRole(role, Set.of());
    }

    @Transactional
    public RoleResponse updateRole(UUID actor, UUID roleId, UpdateRoleRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        RoleStatus status = parseStatus(request.status());
        Role role = authorizationService.updateRole(actor, roleId, request.roleName().trim(),
                request.description(), status);
        Set<String> codes = new HashSet<>();
        authorizationService.rolePermissions(roleId)
                .forEach(permission -> codes.add(permission.getPermissionCode()));
        return toRole(role, codes);
    }

    @Transactional
    public RoleResponse setPermissions(UUID actor, UUID roleId, Set<UUID> permissionIds) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        Role role = authorizationService.getRole(roleId);
        authorizationService.setRolePermissions(actor, roleId, permissionIds);
        Set<String> codes = new HashSet<>();
        authorizationService.rolePermissions(roleId)
                .forEach(permission -> codes.add(permission.getPermissionCode()));
        return toRole(role, codes);
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentView> assignments(UUID actor, UUID userId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("User tidak ditemukan.");
        }
        List<UserRoleAssignment> assignments = authorizationService.userAssignments(userId);
        Map<UUID, Role> roles = authorizationService.rolesByIds(assignments.stream()
                .map(UserRoleAssignment::getRoleId).toList());
        return assignments.stream().map(assignment -> {
            Role role = roles.get(assignment.getRoleId());
            return new RoleAssignmentView(assignment.getId(), assignment.getRoleId(),
                    role == null ? "UNKNOWN" : role.getRoleCode(),
                    role == null ? "Role tidak ditemukan" : role.getRoleName(),
                    assignment.getScopeType().name(), assignment.getOrganizationUnitId(),
                    assignment.getWorkspaceId(), assignment.isIncludeDescendants(), assignment.getValidFrom(),
                    assignment.getValidUntil(), assignment.getAssignmentStatus().name(), assignment.getCreatedAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<RoleHolderView> roleAssignments(UUID actor, UUID roleId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        authorizationService.getRole(roleId);
        List<UserRoleAssignment> assignments = assignmentRepository.findAllByRoleIdOrderByCreatedAtDesc(roleId);
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(assignments.stream().map(UserRoleAssignment::getUserId).distinct().toList())
                .forEach(user -> users.put(user.getId(), user));
        Map<UUID, OrganizationUnit> units = new HashMap<>();
        organizationUnitRepository.findAllById(assignments.stream()
                        .map(UserRoleAssignment::getOrganizationUnitId).filter(Objects::nonNull)
                        .distinct().toList())
                .forEach(unit -> units.put(unit.getId(), unit));
        Map<UUID, String> workspaceNames = new HashMap<>();
        assignments.stream().map(UserRoleAssignment::getWorkspaceId).filter(Objects::nonNull)
                .distinct().forEach(workspaceId -> workspaceLookup.workspaceName(workspaceId)
                        .ifPresent(name -> workspaceNames.put(workspaceId, name)));
        return assignments.stream().map(assignment -> {
            User user = users.get(assignment.getUserId());
            OrganizationUnit unit = assignment.getOrganizationUnitId() == null
                    ? null : units.get(assignment.getOrganizationUnitId());
            return new RoleHolderView(
                    assignment.getId(), assignment.getUserId(),
                    user == null ? "UNKNOWN" : user.getUsername(),
                    user == null ? "User tidak ditemukan" : user.getDisplayName(),
                    user == null ? "INACTIVE" : user.getAccountStatus().name(),
                    assignment.getScopeType().name(), assignment.getOrganizationUnitId(),
                    unit == null ? null : unit.getUnitCode(),
                    unit == null ? null : unit.getUnitName(),
                    unit == null || unit.getUnitType() == null ? null : unit.getUnitType().name(),
                    assignment.getWorkspaceId(),
                    assignment.getWorkspaceId() == null ? null : workspaceNames.get(assignment.getWorkspaceId()),
                    assignment.isIncludeDescendants(), assignment.getValidFrom(), assignment.getValidUntil(),
                    assignment.getAssignmentStatus().name(), assignment.getCreatedAt());
        }).toList();
    }

    @Transactional
    public RoleAssignmentView assign(UUID actor, UUID userId, AssignRoleRequest request) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        if (!userRepository.existsById(userId)) {
            throw ApiException.notFound("User tidak ditemukan.");
        }
        UserRoleAssignment assignment = authorizationService.assignRole(actor, userId, request.roleCode(),
                request.scopeType(), request.organizationUnitId(), request.workspaceId(),
                Boolean.TRUE.equals(request.includeDescendants()), request.validUntil());
        Role role = authorizationService.getRole(assignment.getRoleId());
        return new RoleAssignmentView(assignment.getId(), assignment.getRoleId(), role.getRoleCode(),
                role.getRoleName(), assignment.getScopeType().name(), assignment.getOrganizationUnitId(),
                assignment.getWorkspaceId(), assignment.isIncludeDescendants(), assignment.getValidFrom(),
                assignment.getValidUntil(), assignment.getAssignmentStatus().name(), assignment.getCreatedAt());
    }

    @Transactional
    public void revoke(UUID actor, UUID assignmentId) {
        authorizationService.requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);
        UserRoleAssignment assignment = authorizationService.findAssignment(assignmentId);
        if (actor.equals(assignment.getUserId()) && isLastSystemAdmin(actor, assignment)) {
            throw new ApiException(ErrorCode.ACTION_FORBIDDEN,
                    "Anda tidak dapat mencabut assignment administrator terakhir milik sendiri.");
        }
        authorizationService.revokeAssignment(actor, assignmentId);
    }

    private boolean isLastSystemAdmin(UUID actor, UserRoleAssignment assignment) {
        if (assignment.getAssignmentStatus() != com.kcic.connect.authorization.domain.AssignmentStatus.ACTIVE) {
            return false;
        }
        if (!PROTECTED_GOVERNANCE_ROLES.contains(
                authorizationService.getRole(assignment.getRoleId()).getRoleCode())) {
            return false;
        }
        long remaining = authorizationService.userAssignments(actor).stream()
                .filter(item -> item.getAssignmentStatus()
                        == com.kcic.connect.authorization.domain.AssignmentStatus.ACTIVE)
                .filter(item -> !item.getId().equals(assignment.getId()))
                .filter(item -> PROTECTED_GOVERNANCE_ROLES.contains(
                        authorizationService.getRole(item.getRoleId()).getRoleCode()))
                .count();
        return remaining == 0;
    }

    private RoleStatus parseStatus(String value) {
        try {
            return RoleStatus.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Data tidak valid.",
                    List.of(new com.kcic.connect.shared.api.FieldErrorItem("status", "Status role tidak valid.")));
        }
    }

    private RoleResponse toRole(Role role, Set<String> permissions) {
        return new RoleResponse(role.getId(), role.getRoleCode(), role.getRoleName(), role.getDescription(),
                role.isSystemRole(), role.getRoleStatus().name(), permissions.stream().sorted().toList());
    }

    private PermissionResponse toPermission(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getPermissionCode(),
                permission.getPermissionName(), permission.getDescription(), permission.getModuleCode());
    }
}
