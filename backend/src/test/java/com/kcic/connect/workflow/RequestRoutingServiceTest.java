package com.kcic.connect.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.application.DocumentService;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.workflow.application.RequestRoutingService;
import com.kcic.connect.workflow.domain.RequestType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RequestRoutingServiceTest {

    private DocumentService documentService;
    private AuthorizationService authorizationService;
    private UserRepository userRepository;
    private RequestRoutingService service;

    private final UUID unitId = UUID.randomUUID();
    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        documentService = mock(DocumentService.class);
        authorizationService = mock(AuthorizationService.class);
        userRepository = mock(UserRepository.class);
        OrganizationUnitRepository organizationUnitRepository = mock(OrganizationUnitRepository.class);
        service = new RequestRoutingService(documentService, authorizationService, userRepository,
                organizationUnitRepository);

        Document document = mock(Document.class);
        when(document.getOwnerOrganizationUnitId()).thenReturn(unitId);
        when(document.getOwnerUserId()).thenReturn(null);
        when(documentService.loadDocument(documentId)).thenReturn(document);
    }

    private User activeUser(String username) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn(username);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        return user;
    }

    private void stubRole(String roleCode, ScopeType scopeType, UUID scopeId, User user) {
        UUID userId = user.getId();
        when(authorizationService.activeUserIdsWithRole(roleCode, scopeType, scopeId))
                .thenReturn(List.of(userId));
        when(userRepository.findAllById(List.of(userId))).thenReturn(List.of(user));
    }

    private void stubEmptyRole(String roleCode, ScopeType scopeType, UUID scopeId) {
        when(authorizationService.activeUserIdsWithRole(roleCode, scopeType, scopeId))
                .thenReturn(List.of());
    }

    @Test
    void routesToUnitPicWhenNoUnitManagerExists() {
        User pic = activeUser("pic1");
        stubEmptyRole("UNIT_MANAGER", ScopeType.ORGANIZATION_UNIT, unitId);
        stubRole("PIC", ScopeType.ORGANIZATION_UNIT, unitId, pic);

        RequestRoutingService.Routing routing =
                service.resolve(RequestType.ACCESS, documentId, null, null);

        assertThat(routing.approverUserId()).isEqualTo(pic.getId());
    }

    @Test
    void routesToGlobalAdminWhenNoUnitApproverExists() {
        User admin = activeUser("admin1");
        stubEmptyRole("UNIT_MANAGER", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("PIC", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("DOCUMENT_ADMIN", ScopeType.GLOBAL, null);
        stubRole("ADMIN", ScopeType.GLOBAL, null, admin);

        RequestRoutingService.Routing routing =
                service.resolve(RequestType.ACCESS, documentId, null, null);

        assertThat(routing.approverUserId()).isEqualTo(admin.getId());
    }

    @Test
    void routesToAdministratorAsGovernanceFallback() {
        User administrator = activeUser("administrator1");
        stubEmptyRole("UNIT_MANAGER", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("PIC", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("DOCUMENT_ADMIN", ScopeType.GLOBAL, null);
        stubEmptyRole("ADMIN", ScopeType.GLOBAL, null);
        stubEmptyRole("SYSTEM_ADMIN", ScopeType.GLOBAL, null);
        stubRole("ADMINISTRATOR", ScopeType.GLOBAL, null, administrator);

        RequestRoutingService.Routing routing =
                service.resolve(RequestType.ACCESS, documentId, null, null);

        assertThat(routing.approverUserId()).isEqualTo(administrator.getId());
    }

    @Test
    void prefersDocumentOwnerOverAnyRole() {
        User owner = activeUser("owner1");
        UUID ownerUserId = owner.getId();
        when(userRepository.findById(ownerUserId)).thenReturn(java.util.Optional.of(owner));

        RequestRoutingService.Routing routing =
                service.resolve(RequestType.ACCESS, documentId, ownerUserId, null);

        assertThat(routing.approverUserId()).isEqualTo(ownerUserId);
    }

    @Test
    void failsWithConflictWhenNoApproverAvailable() {
        stubEmptyRole("UNIT_MANAGER", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("PIC", ScopeType.ORGANIZATION_UNIT, unitId);
        stubEmptyRole("DOCUMENT_ADMIN", ScopeType.GLOBAL, null);
        stubEmptyRole("ADMIN", ScopeType.GLOBAL, null);
        stubEmptyRole("SYSTEM_ADMIN", ScopeType.GLOBAL, null);
        stubEmptyRole("ADMINISTRATOR", ScopeType.GLOBAL, null);

        assertThatThrownBy(() -> service.resolve(RequestType.ACCESS, documentId, null, null))
                .isInstanceOf(ApiException.class);
    }
}