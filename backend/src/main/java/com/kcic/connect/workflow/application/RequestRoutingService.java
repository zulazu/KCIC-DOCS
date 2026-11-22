package com.kcic.connect.workflow.application;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.ScopeType;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.application.DocumentService;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.workflow.domain.RequestType;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Resolves the reviewer for a new request: target document owner first, then the
 * unit manager of the owning unit, then an active document administrator or system
 * administrator. The resolver never assigns a request to an inactive account.
 */
@Component
public class RequestRoutingService {

    private static final String UNIT_MANAGER = "UNIT_MANAGER";
    private static final String DOCUMENT_ADMIN = "DOCUMENT_ADMIN";
    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final DocumentService documentService;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

    public RequestRoutingService(DocumentService documentService,
                                 AuthorizationService authorizationService,
                                 UserRepository userRepository,
                                 OrganizationUnitRepository organizationUnitRepository) {
        this.documentService = documentService;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
    }

    public record Routing(Document document, UUID ownerUnitId, UUID ownerUserId, UUID approverUserId) {
    }

    public Routing resolve(RequestType requestType, UUID targetDocumentId, UUID targetOwnerUserId,
                           UUID targetOrganizationUnitId) {
        Document document = targetDocumentId == null ? null : documentService.loadDocument(targetDocumentId);
        UUID unitId = targetOrganizationUnitId;
        if (unitId == null && document != null) {
            unitId = document.getOwnerOrganizationUnitId();
        }
        if (targetOrganizationUnitId != null) {
            requireActiveUnit(targetOrganizationUnitId);
        }
        UUID ownerUserId = targetOwnerUserId;
        if (ownerUserId == null && document != null) {
            ownerUserId = document.getOwnerUserId();
        }
        if (ownerUserId != null) {
            requireActiveUser(ownerUserId);
            return new Routing(document, unitId, ownerUserId, ownerUserId);
        }
        if (unitId != null) {
            UUID manager = firstActive(authorizationService.activeUserIdsWithRole(UNIT_MANAGER,
                    ScopeType.ORGANIZATION_UNIT, unitId));
            if (manager != null) {
                return new Routing(document, unitId, ownerUserId, manager);
            }
        }
        UUID documentAdmin = firstActive(authorizationService.activeUserIdsWithRole(DOCUMENT_ADMIN,
                ScopeType.GLOBAL, null));
        if (documentAdmin == null && unitId != null) {
            documentAdmin = firstActive(authorizationService.activeUserIdsWithRole(DOCUMENT_ADMIN,
                    ScopeType.ORGANIZATION_UNIT, unitId));
        }
        if (documentAdmin == null) {
            documentAdmin = firstActive(authorizationService.activeUserIdsWithRole(SYSTEM_ADMIN,
                    ScopeType.GLOBAL, null));
        }
        if (documentAdmin == null) {
            throw ApiException.conflict("Belum ada approver aktif untuk request " + requestType
                    + ". Hubungi administrator.");
        }
        return new Routing(document, unitId, ownerUserId, documentAdmin);
    }

    private void requireActiveUnit(UUID unitId) {
        OrganizationUnit unit = organizationUnitRepository.findById(unitId)
                .orElseThrow(() -> ApiException.validation("targetOrganizationUnitId", "Unit tidak ditemukan."));
        if (unit.getUnitStatus() != UnitStatus.ACTIVE) {
            throw ApiException.validation("targetOrganizationUnitId", "Unit tidak aktif.");
        }
    }

    private void requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.validation("targetOwnerUserId", "Pengguna tidak ditemukan."));
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw ApiException.validation("targetOwnerUserId", "Pengguna tidak aktif.");
        }
    }

    private UUID firstActive(List<UUID> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        return userRepository.findAllById(candidates).stream()
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .min(Comparator.comparing(User::getUsername))
                .map(User::getId)
                .orElse(null);
    }
}
