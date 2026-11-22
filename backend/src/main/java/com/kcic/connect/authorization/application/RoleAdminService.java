package com.kcic.connect.authorization.application;

import com.kcic.connect.authorization.api.RoleDtos.AssignRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.CreateRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.PermissionResponse;
import com.kcic.connect.authorization.api.RoleDtos.RoleResponse;
import com.kcic.connect.authorization.api.RoleDtos.UpdateRoleRequest;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.RoleStatus;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.identity.api.UserDtos.RoleAssignmentView;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAdminService {

    private static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

    private final AuthorizationService authorizationService;
    private final com.kcic.connect.identity.infrastructure.UserRepository userRepository;

    public RoleAdminService(AuthorizationService authorizationService,
                            com.kcic.connect.identity.infrastructure.UserRepository userRepository) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
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
        Role existing = authorizationService.getRole(roleId);
        RoleStatus status = parseStatus(request.status());
        if (existing.getRoleCode().equals(SYSTEM_ADMIN_ROLE) && status != RoleStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACTION_FORBIDDEN, "Role sistem utama tidak dapat dinonaktifkan.");
        }
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
        if (role.getRoleCode().equals(SYSTEM_ADMIN_ROLE)) {
            throw new ApiException(ErrorCode.ACTION_FORBIDDEN,
                    "Permission role sistem utama dikelola oleh aplikasi dan tidak dapat diubah.");
        }
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
        if (!authorizationService.getRole(assignment.getRoleId()).getRoleCode().equals(SYSTEM_ADMIN_ROLE)) {
            return false;
        }
        long remaining = authorizationService.userAssignments(actor).stream()
                .filter(item -> item.getAssignmentStatus()
                        == com.kcic.connect.authorization.domain.AssignmentStatus.ACTIVE)
                .filter(item -> !item.getId().equals(assignment.getId()))
                .filter(item -> authorizationService.getRole(item.getRoleId()).getRoleCode()
                        .equals(SYSTEM_ADMIN_ROLE))
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
