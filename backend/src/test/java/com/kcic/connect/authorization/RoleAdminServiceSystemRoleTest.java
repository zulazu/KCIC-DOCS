package com.kcic.connect.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kcic.connect.authorization.api.RoleDtos.RoleResponse;
import com.kcic.connect.authorization.api.RoleDtos.UpdateRoleRequest;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.application.RoleAdminService;
import com.kcic.connect.authorization.application.WorkspaceLookup;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.RoleCodes;
import com.kcic.connect.authorization.domain.RoleStatus;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.authorization.infrastructure.UserRoleAssignmentRepository;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAdminServiceSystemRoleTest {

    private AuthorizationService authorizationService;
    private RoleAdminService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-23T00:00:00Z");

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        service = new RoleAdminService(authorizationService, mock(UserRepository.class),
                mock(UserRoleAssignmentRepository.class), mock(OrganizationUnitRepository.class),
                mock(WorkspaceLookup.class));
    }

    private Role systemAdminRole() {
        return new Role(roleId, RoleCodes.SYSTEM_ADMIN, "Administrator", "Role sistem", true, now);
    }

    @Test
    void systemRolePermissionsCanBeEdited() {
        Role role = systemAdminRole();
        when(authorizationService.getRole(roleId)).thenReturn(role);
        UUID permissionId = UUID.randomUUID();
        Permission permission = mock(Permission.class);
        when(permission.getPermissionCode()).thenReturn("role.manage");
        when(authorizationService.rolePermissions(roleId)).thenReturn(List.of(permission));

        RoleResponse response = service.setPermissions(actor, roleId, Set.of(permissionId));

        verify(authorizationService).setRolePermissions(actor, roleId, Set.of(permissionId));
        assertThat(response.systemRole()).isTrue();
        assertThat(response.permissions()).containsExactly("role.manage");
    }

    @Test
    void systemRoleCanBeDeactivated() {
        Role role = systemAdminRole();
        when(authorizationService.getRole(roleId)).thenReturn(role);
        when(authorizationService.updateRole(eq(actor), eq(roleId), eq("Administrator"), any(),
                eq(RoleStatus.INACTIVE))).thenAnswer(invocation -> {
                    role.update(invocation.getArgument(2), invocation.getArgument(3),
                            invocation.getArgument(4), actor, now);
                    return role;
                });
        when(authorizationService.rolePermissions(roleId)).thenReturn(List.of());

        RoleResponse response = service.updateRole(actor, roleId,
                new UpdateRoleRequest("Administrator", "Role sistem", "INACTIVE"));

        assertThat(response.status()).isEqualTo("INACTIVE");
    }

    @Test
    void lastGovernanceAdminCannotRevokeOwnAssignment() {
        Role role = systemAdminRole();
        when(authorizationService.getRole(roleId)).thenReturn(role);
        UserRoleAssignment assignment = new UserRoleAssignment(UUID.randomUUID(), actor, roleId,
                ScopeType.GLOBAL, null, null, false, now, null, actor);
        when(authorizationService.findAssignment(assignment.getId())).thenReturn(assignment);
        when(authorizationService.userAssignments(actor)).thenReturn(List.of(assignment));

        assertThatThrownBy(() -> service.revoke(actor, assignment.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessage("Anda tidak dapat mencabut assignment administrator terakhir milik sendiri.");
        verify(authorizationService, never()).revokeAssignment(any(), any());
    }

    @Test
    void selfRevokeAllowedWhenAnotherGovernanceAssignmentRemains() {
        Role systemAdmin = systemAdminRole();
        UUID administratorRoleId = UUID.randomUUID();
        Role administrator = new Role(administratorRoleId, RoleCodes.ADMINISTRATOR, "Admin",
                "Role sistem", true, now);
        when(authorizationService.getRole(roleId)).thenReturn(systemAdmin);
        when(authorizationService.getRole(administratorRoleId)).thenReturn(administrator);
        UserRoleAssignment own = new UserRoleAssignment(UUID.randomUUID(), actor, roleId,
                ScopeType.GLOBAL, null, null, false, now, null, actor);
        UserRoleAssignment other = new UserRoleAssignment(UUID.randomUUID(), actor, administratorRoleId,
                ScopeType.GLOBAL, null, null, false, now, null, actor);
        when(authorizationService.findAssignment(own.getId())).thenReturn(own);
        when(authorizationService.userAssignments(actor)).thenReturn(List.of(own, other));

        service.revoke(actor, own.getId());

        verify(authorizationService).revokeAssignment(actor, own.getId());
    }
}