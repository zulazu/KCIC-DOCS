package com.kcic.connect.authorization.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.domain.AclDecision;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.AssignmentStatus;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.RoleStatus;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.authorization.infrastructure.PermissionRepository;
import com.kcic.connect.authorization.infrastructure.ResourcePermissionRepository;
import com.kcic.connect.authorization.infrastructure.RolePermissionRepository;
import com.kcic.connect.authorization.infrastructure.RoleRepository;
import com.kcic.connect.authorization.infrastructure.UserRoleAssignmentRepository;
import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthorizationService {

    private static final Set<String> GOVERNANCE_ONLY_ROLES = com.kcic.connect.authorization.domain.RoleCodes.GLOBAL_ADMIN_ROLES;

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository assignmentRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final PermissionRepository permissionRepository;
    private final ResourcePermissionRepository resourcePermissionRepository;
    private final UserOrganizationAssignmentRepository organizationAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final EmployeePlacementRepository employeePlacementRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkspaceLookup workspaceLookup;
    private final AuditService auditService;
    private final Clock clock;

    public AuthorizationService(UserRepository userRepository,
                                UserRoleAssignmentRepository assignmentRepository,
                                RoleRepository roleRepository,
                                RolePermissionRepository rolePermissionRepository,
                                PermissionRepository permissionRepository,
                                ResourcePermissionRepository resourcePermissionRepository,
                                UserOrganizationAssignmentRepository organizationAssignmentRepository,
                                OrganizationUnitRepository organizationUnitRepository,
                                EmployeePlacementRepository employeePlacementRepository,
                                EmployeeRepository employeeRepository,
                                WorkspaceLookup workspaceLookup,
                                AuditService auditService,
                                Clock clock) {
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.permissionRepository = permissionRepository;
        this.resourcePermissionRepository = resourcePermissionRepository;
        this.organizationAssignmentRepository = organizationAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.employeePlacementRepository = employeePlacementRepository;
        this.employeeRepository = employeeRepository;
        this.workspaceLookup = workspaceLookup;
        this.auditService = auditService;
        this.clock = clock;
    }

    public Set<UUID> effectiveRoleIds(UUID userId) {
        return new HashSet<>(assignmentRepository.findEffectiveRoleIds(userId, AssignmentStatus.ACTIVE, Instant.now(clock)));
    }

    public Set<UUID> activeUnitIds(UUID userId) {
        Set<UUID> units = new LinkedHashSet<>();
        organizationAssignmentRepository.findByUserIdAndIsActiveTrue(userId)
                .forEach(a -> units.add(a.getOrganizationUnitId()));
        userRepository.findById(userId)
                .map(User::getEmployeeId)
                .flatMap(employeePlacementRepository::findFirstByEmployeeIdAndIsActiveTrue)
                .ifPresent(placement -> units.add(placement.getDivisionUnitId()));
        return units;
    }

    public void requireActiveEmployment(User user, String field) {
        if (user.getEmployeeId() == null) {
            return;
        }
        Employee employee = employeeRepository.findById(user.getEmployeeId()).orElse(null);
        if (employee != null && employee.getEmploymentStatus() != EmploymentStatus.ACTIVE) {
            throw ApiException.validation(field, "User tidak dapat menerima akses baru karena status "
                    + "kepegawaian " + employee.getEmploymentStatus().name() + ".");
        }
    }

    public List<Role> effectiveRoles(UUID userId) {
        return roleRepository.findAllById(effectiveRoleIds(userId)).stream()
                .filter(r -> r.getRoleStatus() == RoleStatus.ACTIVE)
                .sorted((a, b) -> a.getRoleCode().compareTo(b.getRoleCode()))
                .toList();
    }

    public Map<UUID, Role> rolesByIds(Collection<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Role> result = new HashMap<>();
        roleRepository.findAllById(roleIds).forEach(role -> result.put(role.getId(), role));
        return result;
    }

    public List<String> roleCodes(UUID userId) {
        return effectiveRoles(userId).stream().map(Role::getRoleCode).toList();
    }

    public Map<UUID, Set<String>> permissionCodesByRole(Collection<UUID> roleIds) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Set<String>> result = new HashMap<>();
        for (Object[] row : rolePermissionRepository.findPermissionCodesByRoleIds(new ArrayList<>(roleIds))) {
            result.computeIfAbsent((UUID) row[0], key -> new HashSet<>()).add((String) row[1]);
        }
        return result;
    }

    public Set<String> capabilities(UUID userId) {
        Instant now = Instant.now(clock);
        List<UserRoleAssignment> assignments = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now);
        if (assignments.isEmpty()) {
            return Set.of();
        }
        Set<UUID> roleIds = new HashSet<>();
        assignments.forEach(a -> roleIds.add(a.getRoleId()));
        Set<String> activeRoleIds = new HashSet<>();
        roleRepository.findAllById(roleIds).stream()
                .filter(r -> r.getRoleStatus() == RoleStatus.ACTIVE)
                .forEach(r -> activeRoleIds.add(r.getId().toString()));
        Set<String> capabilities = new LinkedHashSet<>();
        permissionCodesByRole(roleIds).forEach((roleId, codes) -> {
            if (activeRoleIds.contains(roleId.toString())) {
                capabilities.addAll(codes);
            }
        });
        return capabilities;
    }

    public boolean hasCapability(UUID userId, String permissionCode) {
        return capabilities(userId).contains(permissionCode);
    }

    /**
     * Active account ids holding the given role at the requested scope. Used by
     * request routing; the caller still validates that the target account is active.
     */
    public List<UUID> activeUserIdsWithRole(String roleCode, ScopeType scopeType, UUID organizationUnitId) {
        return roleRepository.findByRoleCode(roleCode)
                .map(role -> assignmentRepository.findEffectiveUserIdsByRoleAndScope(role.getId(), scopeType,
                        organizationUnitId, AssignmentStatus.ACTIVE, Instant.now(clock)))
                .orElse(List.of());
    }

    public boolean hasGlobalCapability(UUID userId, String permissionCode) {        Instant now = Instant.now(clock);
        List<UserRoleAssignment> assignments = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now).stream()
                .filter(a -> a.getScopeType() == ScopeType.GLOBAL)
                .toList();
        return assignmentsHavePermission(assignments, permissionCode, now);
    }

    /**
     * Resource-level capability: non-global assignments must match the resource scope.
     * Content permissions pass includeGlobal=false so SYSTEM_ADMIN does not read documents
     * without an explicit grant, scope match, or ownership relation.
     */
    public boolean allowsResourceCapability(UUID userId, String permissionCode, UUID workspaceId,
                                            Set<UUID> ownerUnitIds, boolean includeGlobal) {
        Instant now = Instant.now(clock);
        Set<UUID> workspaceUnits = activeWorkspaceUnits(workspaceId);
        List<UserRoleAssignment> effective = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now);
        boolean descendantAware = effective.stream().anyMatch(UserRoleAssignment::isIncludeDescendants);
        Set<UUID> ownerScope = descendantAware ? ancestorUnitIds(ownerUnitIds) : ownerUnitIds;
        Set<UUID> workspaceScope = descendantAware ? ancestorUnitIds(workspaceUnits) : workspaceUnits;
        List<UserRoleAssignment> candidates = effective.stream()
                .filter(a -> includeGlobal || a.getScopeType() != ScopeType.GLOBAL)
                .filter(a -> switch (a.getScopeType()) {
                    case GLOBAL -> true;
                    case WORKSPACE -> a.getWorkspaceId() != null && a.getWorkspaceId().equals(workspaceId);
                    case ORGANIZATION_UNIT -> matchesUnitScope(a, ownerUnitIds, ownerScope)
                            || matchesUnitScope(a, workspaceUnits, workspaceScope);
                })
                .toList();
        return assignmentsHavePermission(candidates, permissionCode, now);
    }

    private Set<UUID> activeWorkspaceUnits(UUID workspaceId) {
        return workspaceLookup.workspaceOwnerUnit(workspaceId).map(Set::of).orElse(Set.of());
    }

    /**
     * Capability check for actions that create a new resource under one or more
     * organization units (no workspace exists yet to anchor a resource check).
     */
    public boolean allowsUnitCapability(UUID userId, String permissionCode, Set<UUID> unitIds) {
        Instant now = Instant.now(clock);
        List<UserRoleAssignment> effective = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now);
        boolean descendantAware = effective.stream().anyMatch(UserRoleAssignment::isIncludeDescendants);
        Set<UUID> unitScope = descendantAware ? ancestorUnitIds(unitIds) : unitIds;
        List<UserRoleAssignment> candidates = effective.stream()
                .filter(a -> a.getScopeType() == ScopeType.ORGANIZATION_UNIT)
                .filter(a -> matchesUnitScope(a, unitIds, unitScope))
                .toList();
        return assignmentsHavePermission(candidates, permissionCode, now);
    }

    private Set<UUID> ancestorUnitIds(Set<UUID> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(organizationUnitRepository.findAncestorIds(unitIds));
    }

    /**
     * Descendant scope only widens a unit grant when the flag is set; legacy rows
     * (flag false) keep exact membership semantics.
     */
    private static boolean matchesUnitScope(UserRoleAssignment assignment, Set<UUID> exactUnits,
                                            Set<UUID> ancestorUnits) {
        UUID scopedUnit = assignment.getOrganizationUnitId();
        if (scopedUnit == null) {
            return false;
        }
        return exactUnits.contains(scopedUnit)
                || (assignment.isIncludeDescendants() && ancestorUnits.contains(scopedUnit));
    }

    private boolean assignmentsHavePermission(List<UserRoleAssignment> assignments, String permissionCode,
                                              Instant now) {
        if (assignments.isEmpty()) {
            return false;
        }
        Set<UUID> roleIds = new HashSet<>();
        assignments.forEach(a -> roleIds.add(a.getRoleId()));
        Set<UUID> activeRoles = new HashSet<>();
        roleRepository.findAllById(roleIds).stream()
                .filter(r -> r.getRoleStatus() == RoleStatus.ACTIVE)
                .forEach(r -> activeRoles.add(r.getId()));
        return permissionCodesByRole(roleIds).entrySet().stream()
                .filter(e -> activeRoles.contains(e.getKey()))
                .anyMatch(e -> e.getValue().contains(permissionCode));
    }

    public void requireGlobalCapability(UUID userId, String permissionCode) {
        if (!hasGlobalCapability(userId, permissionCode)) {
            throw new ApiException(ErrorCode.ACTION_FORBIDDEN, "Anda tidak memiliki izin untuk tindakan ini.");
        }
    }

    /**
     * Content capability check. ARCHITECTURE §8.3 states System Administrator does not
     * automatically read all document content, so assignments of that role are excluded
     * while other global roles keep working. Scope is still enforced by the caller.
     */
    public boolean hasContentCapability(UUID userId, String permissionCode) {
        Instant now = Instant.now(clock);
        List<UserRoleAssignment> assignments = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now);
        if (assignments.isEmpty()) {
            return false;
        }
        Set<UUID> roleIds = new HashSet<>();
        assignments.forEach(a -> roleIds.add(a.getRoleId()));
        Set<UUID> eligible = eligibleContentRoleIds(roleIds);
        if (eligible.isEmpty()) {
            return false;
        }
        return permissionCodesByRole(eligible).values().stream()
                .anyMatch(codes -> codes.contains(permissionCode));
    }

    /**
     * Content capability limited to the given workspace or owner units. Global
     * assignments count unless they come from the SYSTEM_ADMIN role.
     */
    public boolean allowsContentCapability(UUID userId, String permissionCode, UUID workspaceId,
                                           Set<UUID> ownerUnitIds) {
        Instant now = Instant.now(clock);
        List<UserRoleAssignment> assignments = assignmentRepository.findEffectiveByUserId(userId,
                AssignmentStatus.ACTIVE, now);
        if (assignments.isEmpty()) {
            return false;
        }
        Set<UUID> eligibleRoles = eligibleContentRoleIds(
                assignments.stream().map(UserRoleAssignment::getRoleId).collect(java.util.stream.Collectors.toSet()));
        if (eligibleRoles.isEmpty()) {
            return false;
        }
        Set<UUID> workspaceUnits = workspaceId == null ? Set.of() : activeWorkspaceUnits(workspaceId);
        boolean descendantAware = assignments.stream().anyMatch(UserRoleAssignment::isIncludeDescendants);
        Set<UUID> ownerScope = descendantAware ? ancestorUnitIds(ownerUnitIds) : ownerUnitIds;
        Set<UUID> workspaceScope = descendantAware ? ancestorUnitIds(workspaceUnits) : workspaceUnits;
        Set<UUID> scopedRoleIds = assignments.stream()
                .filter(a -> eligibleRoles.contains(a.getRoleId()))
                .filter(a -> switch (a.getScopeType()) {
                    case GLOBAL -> true;
                    case WORKSPACE -> workspaceId != null && workspaceId.equals(a.getWorkspaceId());
                    case ORGANIZATION_UNIT -> matchesUnitScope(a, ownerUnitIds, ownerScope)
                            || matchesUnitScope(a, workspaceUnits, workspaceScope);
                })
                .map(UserRoleAssignment::getRoleId)
                .collect(java.util.stream.Collectors.toSet());
        if (scopedRoleIds.isEmpty()) {
            return false;
        }
        return permissionCodesByRole(scopedRoleIds).values().stream()
                .anyMatch(codes -> codes.contains(permissionCode));
    }

    private Set<UUID> eligibleContentRoleIds(Set<UUID> roleIds) {
        return roleRepository.findAllById(roleIds).stream()
                .filter(r -> r.getRoleStatus() == RoleStatus.ACTIVE)
                .filter(r -> !GOVERNANCE_ONLY_ROLES.contains(r.getRoleCode()))
                .map(Role::getId)
                .collect(java.util.stream.Collectors.toSet());
    }

    public void requireCapability(UUID userId, String permissionCode) {
        if (!hasCapability(userId, permissionCode)) {
            throw new ApiException(ErrorCode.ACTION_FORBIDDEN, "Anda tidak memiliki izin untuk tindakan ini.");
        }
    }

    // ---------------------------------------------------------------- roles

    public List<Role> listRoles() {
        return roleRepository.findAllByOrderByRoleCodeAsc();
    }

    public Role getRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Role tidak ditemukan."));
    }

    public List<Permission> listPermissions() {
        return permissionRepository.findAllByOrderByModuleCodeAscPermissionCodeAsc();
    }

    public List<Permission> rolePermissions(UUID roleId) {
        getRole(roleId);
        Set<UUID> ids = new HashSet<>();
        rolePermissionRepository.findByRoleId(roleId).forEach(rp -> ids.add(rp.getPermissionId()));
        return permissionRepository.findAllById(ids).stream()
                .sorted((a, b) -> a.getPermissionCode().compareTo(b.getPermissionCode()))
                .toList();
    }

    @Transactional
    public Role createRole(UUID actor, String roleCode, String roleName, String description) {
        String normalized = roleCode == null ? "" : roleCode.trim().toUpperCase();
        if (!normalized.matches("[A-Z][A-Z0-9_]{2,79}")) {
            throw ApiException.validation("roleCode", "Kode role harus huruf besar, angka, atau garis bawah.");
        }
        if (roleRepository.findByRoleCode(normalized).isPresent()) {
            throw ApiException.conflict("Kode role sudah digunakan.");
        }
        Instant now = Instant.now(clock);
        Role role = roleRepository.save(new Role(UUID.randomUUID(), normalized, roleName, description, false, now));
        auditService.record(actor, "ROLE_CREATED", "ROLE", role.getId(), "SUCCESS",
                Map.of("roleCode", normalized));
        return role;
    }

    @Transactional
    public Role updateRole(UUID actor, UUID roleId, String roleName, String description, RoleStatus status) {
        Role role = getRole(roleId);
        role.update(roleName, description, status, actor, Instant.now(clock));
        auditService.record(actor, "ROLE_UPDATED", "ROLE", roleId, "SUCCESS",
                Map.of("roleCode", role.getRoleCode(), "status", status.name()));
        return role;
    }

    @Transactional
    public void setRolePermissions(UUID actor, UUID roleId, Set<UUID> permissionIds) {
        Role role = getRole(roleId);
        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            throw ApiException.validation("permissionIds", "Sebagian permission tidak ditemukan.");
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        Instant now = Instant.now(clock);
        permissions.forEach(p -> rolePermissionRepository.save(
                new com.kcic.connect.authorization.domain.RolePermission(roleId, p.getId(), now)));
        auditService.record(actor, "ROLE_PERMISSIONS_UPDATED", "ROLE", roleId, "SUCCESS",
                Map.of("roleCode", role.getRoleCode(), "permissions", permissions.size()));
    }

    // ---------------------------------------------------------- assignments

    public List<UserRoleAssignment> userAssignments(UUID userId) {
        return assignmentRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
    }

    public UserRoleAssignment findAssignment(UUID assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment role tidak ditemukan."));
    }

    @Transactional
    public UserRoleAssignment assignRole(UUID actor, UUID userId, String roleCode, ScopeType scopeType,
                                         UUID organizationUnitId, UUID workspaceId, Instant validUntil) {
        return assignRole(actor, userId, roleCode, scopeType, organizationUnitId, workspaceId, false, validUntil);
    }

    @Transactional
    public UserRoleAssignment assignRole(UUID actor, UUID userId, String roleCode, ScopeType scopeType,
                                         UUID organizationUnitId, UUID workspaceId, boolean includeDescendants,
                                         Instant validUntil) {
        if (includeDescendants && scopeType != ScopeType.ORGANIZATION_UNIT) {
            throw ApiException.validation("includeDescendants",
                    "Cakupan turunan hanya berlaku untuk scope unit organisasi.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User tidak ditemukan."));
        requireActiveEmployment(user, "userId");
        Role role = roleRepository.findByRoleCode(roleCode)
                .filter(r -> r.getRoleStatus() == RoleStatus.ACTIVE)
                .orElseThrow(() -> ApiException.validation("roleCode", "Role tidak tersedia."));

        if (scopeType == ScopeType.GLOBAL && (organizationUnitId != null || workspaceId != null)) {
            throw ApiException.validation("scopeType", "Scope GLOBAL tidak boleh memiliki unit atau workspace.");
        }
        if (scopeType == ScopeType.ORGANIZATION_UNIT) {
            if (organizationUnitId == null || workspaceId != null) {
                throw ApiException.validation("organizationUnitId", "Scope unit wajib menunjuk satu unit.");
            }
            if (!organizationUnitRepository.existsById(organizationUnitId)) {
                throw ApiException.validation("organizationUnitId", "Unit organisasi tidak ditemukan.");
            }
        }
        if (scopeType == ScopeType.WORKSPACE) {
            if (workspaceId == null || organizationUnitId != null) {
                throw ApiException.validation("workspaceId", "Scope workspace wajib menunjuk satu workspace.");
            }
            if (!workspaceLookup.workspaceExists(workspaceId)) {
                throw ApiException.validation("workspaceId", "Workspace tidak ditemukan.");
            }
        }
        if (assignmentRepository.existsEffectiveAssignment(userId, role.getId(), scopeType,
                organizationUnitId, workspaceId, AssignmentStatus.ACTIVE)) {
            throw ApiException.conflict("Assignment dengan role dan scope yang sama sudah aktif.");
        }
        Instant now = Instant.now(clock);
        UserRoleAssignment assignment = assignmentRepository.save(new UserRoleAssignment(UUID.randomUUID(),
                user.getId(), role.getId(), scopeType, organizationUnitId, workspaceId, includeDescendants,
                now, validUntil, actor));
        auditService.record(actor, "USER_ROLE_ASSIGNED", "USER", userId, "SUCCESS",
                Map.of("roleCode", role.getRoleCode(), "scopeType", scopeType.name(),
                        "includeDescendants", Boolean.toString(includeDescendants)));
        return assignment;
    }

    @Transactional
    public UserRoleAssignment assignRoleInternal(UUID actor, UUID userId, String roleCode) {
        return assignRole(actor, userId, roleCode, ScopeType.GLOBAL, null, null, null);
    }

    @Transactional
    public void revokeAssignment(UUID actor, UUID assignmentId) {
        UserRoleAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound("Assignment tidak ditemukan."));
        assignment.revoke(actor, Instant.now(clock));
        auditService.record(actor, "USER_ROLE_REVOKED", "USER", assignment.getUserId(), "SUCCESS",
                Map.of("assignmentId", assignmentId.toString()));
    }

    // ----------------------------------------------------------- resource ACL

    public List<ResourcePermission> activeResourcePermissions(ResourceType type, UUID resourceId) {
        return resourcePermissionRepository.findByResourceTypeAndResourceIdAndRevokedAtIsNull(type, resourceId);
    }

    public List<ResourcePermission> resourcePermissionHistory(ResourceType type, UUID resourceId) {
        return resourcePermissionRepository.findByResourceTypeAndResourceIdOrderByGrantedAtDesc(type, resourceId);
    }

    public boolean hasActiveUserAllow(ResourceType type, UUID resourceId, UUID userId, String permissionCode) {
        Permission permission = permissionRepository.findByPermissionCode(permissionCode).orElse(null);
        if (permission == null) {
            return false;
        }
        Instant now = Instant.now(clock);
        return activeResourcePermissions(type, resourceId).stream()
                .anyMatch(grant -> grant.isEffectiveAt(now)
                        && grant.getEffect() == AclEffect.ALLOW
                        && grant.getPrincipalType() == PrincipalType.USER
                        && userId.equals(grant.getPrincipalUserId())
                        && permission.getId().equals(grant.getPermissionId()));
    }

    public boolean hasEffectiveAcl(List<ResourcePermission> grants, UUID userId, Set<UUID> roleIds,
                                   Set<UUID> unitIds, String permissionCode, Instant now) {
        return aclDecision(grants, userId, roleIds, unitIds, permissionCode, now) == AclDecision.ALLOW;
    }

    /**
     * Explicit deny wins over any allow, matching ARCHITECTURE §8.3. NONE lets the
     * caller fall back to role/scope evaluation.
     */
    public AclDecision aclDecision(List<ResourcePermission> grants, UUID userId, Set<UUID> roleIds,
                                   Set<UUID> unitIds, String permissionCode, Instant now) {
        Permission permission = permissionRepository.findByPermissionCode(permissionCode).orElse(null);
        if (permission == null) {
            return AclDecision.NONE;
        }
        boolean allowed = false;
        boolean descendantAware = grants.stream().anyMatch(ResourcePermission::isIncludeDescendants);
        Set<UUID> ancestorUnits = descendantAware ? ancestorUnitIds(unitIds) : unitIds;
        for (ResourcePermission grant : grants) {
            if (!grant.isEffectiveAt(now) || !permission.getId().equals(grant.getPermissionId())) {
                continue;
            }
            if (!principalMatches(grant, userId, roleIds, unitIds, ancestorUnits)) {
                continue;
            }
            if (grant.getEffect() == AclEffect.DENY) {
                return AclDecision.DENY;
            }
            allowed = true;
        }
        return allowed ? AclDecision.ALLOW : AclDecision.NONE;
    }

    public Map<UUID, List<ResourcePermission>> activeResourcePermissions(ResourceType type,
                                                                          Collection<UUID> resourceIds) {
        if (resourceIds.isEmpty()) {
            return Map.of();
        }
        return resourcePermissionRepository.findByResourceTypeAndResourceIdInAndRevokedAtIsNull(type, resourceIds)
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(ResourcePermission::getResourceId));
    }

    private boolean principalMatches(ResourcePermission grant, UUID userId, Set<UUID> roleIds, Set<UUID> unitIds,
                                     Set<UUID> ancestorUnits) {
        return switch (grant.getPrincipalType()) {
            case USER -> userId.equals(grant.getPrincipalUserId());
            case ROLE -> roleIds.contains(grant.getPrincipalRoleId());
            case ORGANIZATION_UNIT -> {
                UUID scopedUnit = grant.getPrincipalOrganizationUnitId();
                yield unitIds.contains(scopedUnit)
                        || (grant.isIncludeDescendants() && ancestorUnits.contains(scopedUnit));
            }
        };
    }

    public ResourcePermission grant(UUID actor, ResourceType resourceType, UUID resourceId,
                                    PrincipalType principalType, UUID principalId, String permissionCode,
                                    AclEffect effect, GrantSource source, Instant expiresAt) {
        return grant(actor, resourceType, resourceId, principalType, principalId, false, permissionCode, effect,
                source, expiresAt);
    }

    public ResourcePermission grant(UUID actor, ResourceType resourceType, UUID resourceId,
                                    PrincipalType principalType, UUID principalId, boolean includeDescendants,
                                    String permissionCode, AclEffect effect, GrantSource source, Instant expiresAt) {
        if (includeDescendants && principalType != PrincipalType.ORGANIZATION_UNIT) {
            throw ApiException.validation("includeDescendants",
                    "Cakupan turunan hanya berlaku untuk principal unit organisasi.");
        }
        String principalField;
        UUID userId = null;
        UUID roleId = null;
        UUID unitId = null;
        switch (principalType) {
            case USER -> {
                userId = principalId;
                principalField = "user";
                User recipient = userRepository.findById(principalId)
                        .orElseThrow(() -> ApiException.validation("principalId",
                                "User penerima grant tidak ditemukan."));
                requireActiveEmployment(recipient, "principalId");
            }
            case ROLE -> {
                roleId = principalId;
                principalField = "role";
                if (!roleRepository.existsById(principalId)) {
                    throw ApiException.validation("principalId", "Role penerima grant tidak ditemukan.");
                }
            }
            case ORGANIZATION_UNIT -> {
                unitId = principalId;
                principalField = "unit";
                if (!organizationUnitRepository.existsById(principalId)) {
                    throw ApiException.validation("principalId", "Unit penerima grant tidak ditemukan.");
                }
            }
            default -> throw ApiException.validation("principalType", "Principal type tidak valid.");
        }
        Permission permission = permissionRepository.findByPermissionCode(permissionCode)
                .orElseThrow(() -> ApiException.validation("permissionCode", "Permission tidak ditemukan."));
        Instant now = Instant.now(clock);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw ApiException.validation("expiresAt", "Waktu kedaluwarsa harus di masa depan.");
        }
        ResourcePermission saved = resourcePermissionRepository.save(new ResourcePermission(UUID.randomUUID(),
                resourceType, resourceId, principalType, userId, roleId, unitId, includeDescendants,
                permission.getId(), effect, actor, source, now, expiresAt));
        auditService.record(actor, effect == AclEffect.ALLOW ? "ACCESS_GRANTED" : "ACCESS_DENIED_GRANT_CREATED",
                resourceType.name(), resourceId, "SUCCESS", Map.of("principalType", principalType.name(),
                        "principal", principalId.toString(), "permission", permissionCode,
                        "source", source.name(), "principalField", principalField,
                        "includeDescendants", Boolean.toString(includeDescendants)));
        return saved;
    }

    @Transactional
    public ResourcePermission revokeGrant(UUID actor, UUID grantId) {
        ResourcePermission grant = resourcePermissionRepository.findById(grantId)
                .orElseThrow(() -> ApiException.notFound("Grant akses tidak ditemukan."));
        if (grant.getRevokedAt() == null) {
            grant.revoke(actor, Instant.now(clock));
            auditService.record(actor, "ACCESS_REVOKED", grant.getResourceType().name(), grant.getResourceId(),
                    "SUCCESS", Map.of("grantId", grantId.toString()));
        }
        return grant;
    }

    @Transactional
    public void revokeGrantInternal(UUID actor, UUID grantId) {
        resourcePermissionRepository.findById(grantId).ifPresent(grant -> {
            if (grant.getRevokedAt() == null) {
                grant.revoke(actor, Instant.now(clock));
            }
        });
    }

    public ResourcePermission findGrant(UUID grantId) {
        return resourcePermissionRepository.findById(grantId)
                .orElseThrow(() -> ApiException.notFound("Grant akses tidak ditemukan."));
    }
}
