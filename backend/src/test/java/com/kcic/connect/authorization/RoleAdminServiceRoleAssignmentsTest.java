package com.kcic.connect.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.kcic.connect.authorization.api.RoleDtos.RoleHolderView;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.application.RoleAdminService;
import com.kcic.connect.authorization.application.WorkspaceLookup;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.Role;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import com.kcic.connect.authorization.infrastructure.UserRoleAssignmentRepository;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitType;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAdminServiceRoleAssignmentsTest {

    private AuthorizationService authorizationService;
    private UserRepository userRepository;
    private UserRoleAssignmentRepository assignmentRepository;
    private OrganizationUnitRepository organizationUnitRepository;
    private WorkspaceLookup workspaceLookup;
    private RoleAdminService service;

    private final UUID actor = UUID.randomUUID();
    private final UUID roleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        userRepository = mock(UserRepository.class);
        assignmentRepository = mock(UserRoleAssignmentRepository.class);
        organizationUnitRepository = mock(OrganizationUnitRepository.class);
        workspaceLookup = mock(WorkspaceLookup.class);
        service = new RoleAdminService(authorizationService, userRepository, assignmentRepository,
                organizationUnitRepository, workspaceLookup);
        when(authorizationService.getRole(roleId)).thenReturn(mock(Role.class));
    }

    @Test
    void requiresRoleManageCapability() {
        doThrow(new ApiException(ErrorCode.ACTION_FORBIDDEN, "Akses ditolak."))
                .when(authorizationService).requireGlobalCapability(actor, PermissionCodes.ROLE_MANAGE);

        assertThatThrownBy(() -> service.roleAssignments(actor, roleId))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(assignmentRepository);
    }

    @Test
    void unknownRoleIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(authorizationService.getRole(missing))
                .thenThrow(ApiException.notFound("Role tidak ditemukan."));

        assertThatThrownBy(() -> service.roleAssignments(actor, missing))
                .isInstanceOf(ApiException.class)
                .hasMessage("Role tidak ditemukan.");
    }

    @Test
    void mapsUnitScopeWithDescendantsAndUserFacts() {
        UUID userId = UUID.randomUUID();
        UUID unitId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T00:00:00Z");
        UserRoleAssignment assignment = new UserRoleAssignment(UUID.randomUUID(), userId, roleId,
                ScopeType.ORGANIZATION_UNIT, unitId, null, true, now, null, actor);
        when(assignmentRepository.findAllByRoleIdOrderByCreatedAtDesc(roleId))
                .thenReturn(List.of(assignment));
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("pic.operasional");
        when(user.getDisplayName()).thenReturn("PIC Operasional");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
        OrganizationUnit unit = mock(OrganizationUnit.class);
        when(unit.getId()).thenReturn(unitId);
        when(unit.getUnitCode()).thenReturn("DIR-OPS");
        when(unit.getUnitName()).thenReturn("Direktorat Operasional");
        when(unit.getUnitType()).thenReturn(UnitType.DIRECTORATE);
        when(organizationUnitRepository.findAllById(anyList())).thenReturn(List.of(unit));

        List<RoleHolderView> views = service.roleAssignments(actor, roleId);

        assertThat(views).hasSize(1);
        RoleHolderView view = views.get(0);
        assertThat(view.username()).isEqualTo("pic.operasional");
        assertThat(view.displayName()).isEqualTo("PIC Operasional");
        assertThat(view.accountStatus()).isEqualTo("ACTIVE");
        assertThat(view.scopeType()).isEqualTo("ORGANIZATION_UNIT");
        assertThat(view.organizationUnitCode()).isEqualTo("DIR-OPS");
        assertThat(view.organizationUnitName()).isEqualTo("Direktorat Operasional");
        assertThat(view.organizationUnitType()).isEqualTo("DIRECTORATE");
        assertThat(view.includeDescendants()).isTrue();
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.workspaceId()).isNull();
        assertThat(view.workspaceName()).isNull();
    }

    @Test
    void mapsWorkspaceScopeAndKeepsRevokedHistory() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-23T00:00:00Z");
        UserRoleAssignment active = new UserRoleAssignment(UUID.randomUUID(), userId, roleId,
                ScopeType.WORKSPACE, null, workspaceId, false, now, null, actor);
        UserRoleAssignment revoked = new UserRoleAssignment(UUID.randomUUID(), userId, roleId,
                ScopeType.GLOBAL, null, null, false, now, null, actor);
        revoked.revoke(actor, now);
        when(assignmentRepository.findAllByRoleIdOrderByCreatedAtDesc(roleId))
                .thenReturn(List.of(active, revoked));
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("pic.operasional");
        when(user.getDisplayName()).thenReturn("PIC Operasional");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(userRepository.findAllById(anyList())).thenReturn(List.of(user));
        when(organizationUnitRepository.findAllById(anyList())).thenReturn(List.of());
        when(workspaceLookup.workspaceName(workspaceId)).thenReturn(Optional.of("Workspace Operasional"));

        List<RoleHolderView> views = service.roleAssignments(actor, roleId);

        assertThat(views).hasSize(2);
        assertThat(views.get(0).workspaceName()).isEqualTo("Workspace Operasional");
        assertThat(views.get(0).organizationUnitName()).isNull();
        assertThat(views.get(1).scopeType()).isEqualTo("GLOBAL");
        assertThat(views.get(1).status()).isEqualTo("REVOKED");
    }

    @Test
    void emptyRoleReturnsEmptyList() {
        when(assignmentRepository.findAllByRoleIdOrderByCreatedAtDesc(roleId)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(organizationUnitRepository.findAllById(any())).thenReturn(List.of());

        assertThat(service.roleAssignments(actor, roleId)).isEmpty();
    }
}