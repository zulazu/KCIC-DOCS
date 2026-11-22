package com.kcic.connect.repository.application;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.RoleStatus;
import com.kcic.connect.authorization.infrastructure.RoleRepository;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.api.DirectoryDtos.DirectoryResponse;
import com.kcic.connect.repository.api.DirectoryDtos.PrincipalOption;
import com.kcic.connect.shared.api.ApiException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only principal lookup for the access-management dialog. It is not an admin
 * directory: only identifiers and display names of active principals are exposed,
 * and only to principals that can manage access somewhere.
 */
@Service
public class AccessDirectoryService {

    private static final Set<String> DIRECTORY_PERMISSIONS = Set.of(
            PermissionCodes.DOCUMENT_MANAGE_ACCESS,
            PermissionCodes.WORKSPACE_MANAGE,
            PermissionCodes.FOLDER_UPDATE,
            PermissionCodes.DISTRIBUTION_CREATE);

    private static final int USER_RESULT_LIMIT = 20;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final AuthorizationService authorizationService;

    public AccessDirectoryService(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  OrganizationUnitRepository organizationUnitRepository,
                                  AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public DirectoryResponse lookup(UUID actor, String keyword) {
        requireDirectoryAccess(actor);
        String term = keyword == null ? "" : keyword.trim();
        List<PrincipalOption> users = userRepository
                .searchByStatus(term, AccountStatus.ACTIVE, PageRequest.of(0, USER_RESULT_LIMIT))
                .stream()
                .map(user -> new PrincipalOption(user.getId(), user.getDisplayName(), user.getUsername()))
                .toList();
        List<PrincipalOption> roles = roleRepository.findAllByOrderByRoleCodeAsc().stream()
                .filter(role -> role.getRoleStatus() == RoleStatus.ACTIVE)
                .map(role -> new PrincipalOption(role.getId(), role.getRoleName(), role.getRoleCode()))
                .toList();
        List<PrincipalOption> units = organizationUnitRepository.findAllByOrderByUnitNameAsc().stream()
                .filter(unit -> unit.getUnitStatus() == UnitStatus.ACTIVE)
                .map(unit -> new PrincipalOption(unit.getId(), unit.getUnitName(), unit.getUnitCode()))
                .toList();
        return new DirectoryResponse(users, roles, units);
    }

    private void requireDirectoryAccess(UUID actor) {
        Map<UUID, Set<String>> codes = authorizationService
                .permissionCodesByRole(authorizationService.effectiveRoleIds(actor));
        boolean allowed = codes.values().stream()
                .anyMatch(codesForRole -> !Collections.disjoint(codesForRole, DIRECTORY_PERMISSIONS));
        if (!allowed) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat direktori akses.");
        }
    }
}
