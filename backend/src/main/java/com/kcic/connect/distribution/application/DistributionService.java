package com.kcic.connect.distribution.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.Permission;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.authorization.infrastructure.PermissionRepository;
import com.kcic.connect.distribution.api.DistributionDtos.CreateDistributionRequest;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionDetailResponse;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionEventResponse;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionItemRequest;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionItemResponse;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionRecipientResponse;
import com.kcic.connect.distribution.api.DistributionDtos.DistributionSummaryResponse;
import com.kcic.connect.distribution.domain.Distribution;
import com.kcic.connect.distribution.domain.DistributionAccessGrant;
import com.kcic.connect.distribution.domain.DistributionAccessLevel;
import com.kcic.connect.distribution.domain.DistributionEvent;
import com.kcic.connect.distribution.domain.DistributionEventType;
import com.kcic.connect.distribution.domain.DistributionItem;
import com.kcic.connect.distribution.domain.DistributionRecipient;
import com.kcic.connect.distribution.domain.DistributionStatus;
import com.kcic.connect.distribution.domain.RecipientStatus;
import com.kcic.connect.distribution.infrastructure.DistributionAccessGrantRepository;
import com.kcic.connect.distribution.infrastructure.DistributionEventRepository;
import com.kcic.connect.distribution.infrastructure.DistributionItemRepository;
import com.kcic.connect.distribution.infrastructure.DistributionRecipientRepository;
import com.kcic.connect.distribution.infrastructure.DistributionRepository;
import com.kcic.connect.employee.domain.EmployeePlacement;
import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.employee.infrastructure.EmployeePlacementRepository;
import com.kcic.connect.employee.infrastructure.EmployeeRepository;
import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.notification.application.NotificationService;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.organization.infrastructure.UserOrganizationAssignmentRepository;
import com.kcic.connect.repository.application.DocumentAccessService;
import com.kcic.connect.repository.application.FolderGrantResolver;
import com.kcic.connect.repository.domain.Document;
import com.kcic.connect.repository.domain.DocumentStatus;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.domain.VersionStatus;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.repository.infrastructure.DocumentVersionRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the distribution lifecycle: recipient snapshot, delegated ACL grants with
 * provenance, READ tracking, revoke, and lazy expiry. Grants are created as
 * individual rows per recipient/item/permission and are linked through
 * distribution_access_grants, so revoking this distribution never touches grants
 * created by a request, a manual grant, or another distribution.
 */
@Service
public class DistributionService {

    private static final int MAX_PAGE_SIZE = 50;

    private final DistributionRepository distributionRepository;
    private final DistributionRecipientRepository recipientRepository;
    private final DistributionItemRepository itemRepository;
    private final DistributionAccessGrantRepository accessGrantRepository;
    private final DistributionEventRepository eventRepository;
    private final DistributionCodeGenerator codeGenerator;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final DocumentAccessService documentAccessService;
    private final FolderGrantResolver folderGrantResolver;
    private final AuthorizationService authorizationService;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserOrganizationAssignmentRepository organizationAssignmentRepository;
    private final EmployeePlacementRepository employeePlacementRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public DistributionService(DistributionRepository distributionRepository,
                               DistributionRecipientRepository recipientRepository,
                               DistributionItemRepository itemRepository,
                               DistributionAccessGrantRepository accessGrantRepository,
                               DistributionEventRepository eventRepository,
                               DistributionCodeGenerator codeGenerator,
                               DocumentRepository documentRepository,
                               DocumentVersionRepository versionRepository,
                               DocumentAccessService documentAccessService,
                               FolderGrantResolver folderGrantResolver,
                               AuthorizationService authorizationService,
                               PermissionRepository permissionRepository,
                               UserRepository userRepository,
                               OrganizationUnitRepository organizationUnitRepository,
                               UserOrganizationAssignmentRepository organizationAssignmentRepository,
                               EmployeePlacementRepository employeePlacementRepository,
                               EmployeeRepository employeeRepository,
                               NotificationService notificationService,
                               AuditService auditService,
                               Clock clock) {
        this.distributionRepository = distributionRepository;
        this.recipientRepository = recipientRepository;
        this.itemRepository = itemRepository;
        this.accessGrantRepository = accessGrantRepository;
        this.eventRepository = eventRepository;
        this.codeGenerator = codeGenerator;
        this.documentRepository = documentRepository;
        this.versionRepository = versionRepository;
        this.documentAccessService = documentAccessService;
        this.folderGrantResolver = folderGrantResolver;
        this.authorizationService = authorizationService;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.organizationAssignmentRepository = organizationAssignmentRepository;
        this.employeePlacementRepository = employeePlacementRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- create

    @Transactional
    public DistributionDetailResponse create(UUID actor, CreateDistributionRequest payload) {
        authorizationService.requireCapability(actor, PermissionCodes.DISTRIBUTION_CREATE);
        Instant now = Instant.now(clock);
        String title = payload.title() == null ? "" : payload.title().trim();
        if (title.isEmpty()) {
            throw ApiException.validation("title", "Judul distribusi wajib diisi.");
        }
        String message = payload.message() == null || payload.message().isBlank() ? null : payload.message().trim();
        Instant expiresAt = payload.expiresAt();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw ApiException.validation("expiresAt", "Waktu kedaluwarsa harus di masa depan.");
        }

        Map<UUID, DistributionAccessLevel> levels = requestedItems(payload.items());
        Map<UUID, Document> documents = loadDocuments(levels.keySet());
        Map<UUID, DocumentVersion> currentVersions = currentVersions(levels.keySet());
        Map<UUID, DocumentContext> contexts = loadContexts(documents.values());
        DocumentAccessService.ActorContext actorContext = documentAccessService.context(actor);
        for (UUID documentId : levels.keySet()) {
            Document document = documents.get(documentId);
            DistributionAccessLevel level = levels.get(documentId);
            DocumentContext context = contexts.get(documentId);
            requireDelegable(actorContext, document, context, level);
        }

        List<UUID> recipientIds = resolveRecipients(actor, payload.recipientUserIds(),
                payload.recipientOrganizationUnitIds());
        String senderName = userRepository.findById(actor).map(User::getDisplayName).orElse("Pengirim");

        Distribution distribution = distributionRepository.save(new Distribution(UUID.randomUUID(),
                codeGenerator.nextCode(), actor, title, message, expiresAt, now));
        Map<UUID, DistributionItem> items = new LinkedHashMap<>();
        for (Map.Entry<UUID, DistributionAccessLevel> entry : levels.entrySet()) {
            Permission permission = permissionFor(entry.getValue().itemPermissionCode());
            DistributionItem item = itemRepository.save(new DistributionItem(UUID.randomUUID(),
                    distribution.getId(), entry.getKey(), currentVersions.get(entry.getKey()).getId(),
                    permission.getId(), now));
            items.put(entry.getKey(), item);
        }

        String notificationTitle = "Distribusi: " + title;
        String notificationMessage = senderName + " mengirim " + items.size() + " dokumen kepada Anda.";
        for (UUID recipientId : recipientIds) {
            DistributionRecipient recipient = recipientRepository.save(new DistributionRecipient(
                    UUID.randomUUID(), distribution.getId(), recipientId, now, expiresAt));
            for (Map.Entry<UUID, DistributionAccessLevel> entry : levels.entrySet()) {
                DistributionItem item = items.get(entry.getKey());
                for (String permissionCode : entry.getValue().impliedPermissionCodes()) {
                    ResourcePermission grant = authorizationService.grant(actor, ResourceType.DOCUMENT,
                            entry.getKey(), PrincipalType.USER, recipientId, permissionCode, AclEffect.ALLOW,
                            GrantSource.DISTRIBUTION, expiresAt);
                    accessGrantRepository.save(new DistributionAccessGrant(UUID.randomUUID(),
                            recipient.getId(), item.getId(), grant.getId(), now));
                }
            }
            eventRepository.save(new DistributionEvent(UUID.randomUUID(), distribution.getId(),
                    recipient.getId(), actor, DistributionEventType.SENT, "Distribusi diterima.", now));
            notificationService.create(recipientId, "DISTRIBUTION_RECEIVED", notificationTitle,
                    notificationMessage, "DISTRIBUTION", distribution.getId(),
                    Map.of("distributionCode", distribution.getDistributionCode()));
        }
        auditService.record(actor, "DISTRIBUTION_SENT", "DISTRIBUTION", distribution.getId(), "SUCCESS",
                Map.of("distributionCode", distribution.getDistributionCode(), "itemCount", items.size(),
                        "recipientCount", recipientIds.size(),
                        "expiresAt", expiresAt == null ? "NONE" : expiresAt.toString()));
        return detail(actor, distribution.getId());
    }

    private Map<UUID, DistributionAccessLevel> requestedItems(List<DistributionItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw ApiException.validation("items", "Pilih minimal satu dokumen untuk didistribusikan.");
        }
        Map<UUID, DistributionAccessLevel> levels = new LinkedHashMap<>();
        for (DistributionItemRequest request : requests) {
            if (request == null || request.documentId() == null) {
                throw ApiException.validation("items", "Dokumen distribusi tidak valid.");
            }
            if (levels.putIfAbsent(request.documentId(), parseAccessLevel(request.accessLevel())) != null) {
                throw ApiException.validation("items", "Dokumen yang sama dipilih lebih dari sekali.");
            }
        }
        return levels;
    }

    private static DistributionAccessLevel parseAccessLevel(String value) {
        try {
            return DistributionAccessLevel.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation("items", "Tingkat akses distribusi tidak valid.");
        }
    }

    private Map<UUID, Document> loadDocuments(Collection<UUID> documentIds) {
        Map<UUID, Document> documents = documentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        if (documents.size() != documentIds.size()) {
            throw ApiException.validation("items", "Sebagian dokumen tidak ditemukan.");
        }
        for (Document document : documents.values()) {
            if (document.getDocumentStatus() == DocumentStatus.ARCHIVED) {
                throw ApiException.validation("items",
                        "Dokumen \"" + document.getTitle() + "\" sudah diarsipkan dan tidak dapat didistribusikan.");
            }
        }
        return documents;
    }

    private Map<UUID, DocumentVersion> currentVersions(Collection<UUID> documentIds) {
        Map<UUID, DocumentVersion> versions = versionRepository
                .findAllByDocumentIdInAndVersionStatus(documentIds, VersionStatus.CURRENT).stream()
                .collect(Collectors.toMap(DocumentVersion::getDocumentId, Function.identity()));
        for (UUID documentId : documentIds) {
            if (!versions.containsKey(documentId)) {
                throw ApiException.validation("items",
                        "Dokumen yang dipilih belum memiliki versi current untuk didistribusikan.");
            }
        }
        return versions;
    }

    /**
     * A distribution may only hand out access the sender itself holds at that level,
     * and only for documents the sender is allowed to share.
     */
    private void requireDelegable(DocumentAccessService.ActorContext actor, Document document,
                                  DocumentContext context, DistributionAccessLevel level) {
        boolean view = documentAccessService.canView(actor, document, context.documentGrants(),
                context.folderGrants(), context.workspaceGrants());
        boolean preview = documentAccessService.canPreview(actor, document, context.documentGrants(),
                context.folderGrants(), context.workspaceGrants());
        boolean download = documentAccessService.canDownload(actor, document, context.documentGrants(),
                context.folderGrants(), context.workspaceGrants());
        boolean allowed = switch (level) {
            case VIEW -> view;
            case PREVIEW -> view && preview;
            case DOWNLOAD -> view && preview && download;
        };
        if (!allowed) {
            throw ApiException.forbidden("Anda tidak dapat membagikan \"" + document.getTitle()
                    + "\" dengan tingkat akses tersebut.");
        }
        if (!documentAccessService.canShare(actor, document, context.documentGrants(), context.folderGrants(),
                context.workspaceGrants())) {
            throw ApiException.forbidden("Anda tidak memiliki izin berbagi pada \"" + document.getTitle() + "\".");
        }
    }

    private List<UUID> resolveRecipients(UUID actor, List<UUID> userIds, List<UUID> unitIds) {
        Set<UUID> requestedUsers = new LinkedHashSet<>(userIds == null ? List.of() : userIds);
        Set<UUID> requestedUnits = new LinkedHashSet<>(unitIds == null ? List.of() : unitIds);
        if (requestedUsers.isEmpty() && requestedUnits.isEmpty()) {
            throw ApiException.validation("recipientUserIds", "Pilih minimal satu penerima distribusi.");
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        if (!requestedUsers.isEmpty()) {
            Map<UUID, User> users = userRepository.findAllById(requestedUsers).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            Set<UUID> inactiveEmployment = inactiveEmploymentUserIds(users.values());
            for (UUID userId : requestedUsers) {
                User user = users.get(userId);
                if (user == null) {
                    throw ApiException.validation("recipientUserIds", "Sebagian penerima tidak ditemukan.");
                }
                if (user.getAccountStatus() != AccountStatus.ACTIVE) {
                    throw ApiException.validation("recipientUserIds",
                            "Penerima \"" + user.getDisplayName() + "\" tidak aktif.");
                }
                if (inactiveEmployment.contains(userId)) {
                    throw ApiException.validation("recipientUserIds",
                            "Penerima \"" + user.getDisplayName()
                                    + "\" tidak aktif secara kepegawaian.");
                }
                if (!userId.equals(actor)) {
                    recipients.add(userId);
                }
            }
        }
        // Unit recipients are a snapshot of the unit's active members at send time;
        // members whose account is no longer active are simply left out.
        Set<UUID> unitMembers = new LinkedHashSet<>();
        for (UUID unitId : requestedUnits) {
            OrganizationUnit unit = organizationUnitRepository.findById(unitId)
                    .orElseThrow(() -> ApiException.validation("recipientOrganizationUnitIds",
                            "Unit penerima tidak ditemukan."));
            if (unit.getUnitStatus() != UnitStatus.ACTIVE) {
                throw ApiException.validation("recipientOrganizationUnitIds",
                        "Unit \"" + unit.getUnitName() + "\" tidak aktif.");
            }
            organizationAssignmentRepository.findByOrganizationUnitIdAndIsActiveTrue(unitId)
                    .forEach(assignment -> unitMembers.add(assignment.getUserId()));
            List<UUID> placedEmployeeIds = employeePlacementRepository
                    .findByDivisionUnitIdAndIsActiveTrue(unitId).stream()
                    .map(EmployeePlacement::getEmployeeId)
                    .toList();
            if (!placedEmployeeIds.isEmpty()) {
                userRepository.findByEmployeeIdIn(placedEmployeeIds)
                        .forEach(user -> unitMembers.add(user.getId()));
            }
        }
        unitMembers.remove(actor);
        unitMembers.removeAll(requestedUsers);
        if (!unitMembers.isEmpty()) {
            Map<UUID, User> users = userRepository.findAllById(unitMembers).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            Set<UUID> inactiveEmployment = inactiveEmploymentUserIds(users.values());
            for (UUID userId : unitMembers) {
                User user = users.get(userId);
                if (user != null && user.getAccountStatus() == AccountStatus.ACTIVE
                        && !inactiveEmployment.contains(userId)) {
                    recipients.add(userId);
                }
            }
        }
        if (recipients.isEmpty()) {
            throw ApiException.validation("recipientUserIds",
                    "Pilih minimal satu penerima aktif selain pengirim.");
        }
        return new ArrayList<>(recipients);
    }

    private Set<UUID> inactiveEmploymentUserIds(Collection<User> users) {
        List<UUID> employeeIds = users.stream()
                .map(User::getEmployeeId)
                .filter(employeeId -> employeeId != null)
                .toList();
        if (employeeIds.isEmpty()) {
            return Set.of();
        }
        Map<UUID, EmploymentStatus> statuses = new HashMap<>();
        employeeRepository.findAllById(employeeIds)
                .forEach(employee -> statuses.put(employee.getId(), employee.getEmploymentStatus()));
        return users.stream()
                .filter(user -> user.getEmployeeId() != null)
                .filter(user -> statuses.get(user.getEmployeeId()) != EmploymentStatus.ACTIVE)
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private Permission permissionFor(String permissionCode) {
        return permissionRepository.findByPermissionCode(permissionCode)
                .orElseThrow(() -> ApiException.validation("items", "Permission distribusi tidak tersedia."));
    }

    // ------------------------------------------------------------------ read

    @Transactional
    public PageResponse<DistributionSummaryResponse> list(UUID actor, String box, String status, String keyword,
                                                          int page, int size) {
        boolean received = "received".equalsIgnoreCase(box == null ? "" : box.trim());
        authorizationService.requireCapability(actor,
                received ? PermissionCodes.DISTRIBUTION_VIEW_RECEIVED : PermissionCodes.DISTRIBUTION_VIEW_OWN);
        expireDue(Instant.now(clock));
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Specification<Distribution> spec = received ? receivedBy(actor) : sentBy(actor);
        String term = keyword == null ? "" : keyword.trim();
        if (!term.isEmpty()) {
            String pattern = "%" + term.toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("distributionCode")), pattern)));
        }
        DistributionStatus filter = parseStatus(status);
        if (filter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("distributionStatus"), filter));
        }
        Page<Distribution> result = distributionRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "sentAt")));
        List<DistributionSummaryResponse> items = summaries(result.getContent(), actor, received);
        return PageResponse.of(items, safePage, safeSize, result.getTotalElements());
    }

    private static Specification<Distribution> sentBy(UUID actor) {
        return (root, query, cb) -> cb.equal(root.get("senderUserId"), actor);
    }

    private static Specification<Distribution> receivedBy(UUID actor) {
        return (root, query, cb) -> {
            var subquery = query.subquery(UUID.class);
            var recipient = subquery.from(DistributionRecipient.class);
            subquery.select(recipient.get("distributionId"))
                    .where(cb.equal(recipient.get("recipientUserId"), actor));
            return root.get("id").in(subquery);
        };
    }

    private static DistributionStatus parseStatus(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            return null;
        }
        try {
            return DistributionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw ApiException.validation("status", "Status distribusi tidak valid.");
        }
    }

    private List<DistributionSummaryResponse> summaries(List<Distribution> distributions, UUID actor,
                                                        boolean received) {
        if (distributions.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = distributions.stream().map(Distribution::getId).toList();
        Map<UUID, List<DistributionItem>> itemsByDistribution = itemRepository.findByDistributionIdIn(ids).stream()
                .collect(Collectors.groupingBy(DistributionItem::getDistributionId));
        Map<UUID, List<DistributionRecipient>> recipientsByDistribution = recipientRepository
                .findByDistributionIdIn(ids).stream()
                .collect(Collectors.groupingBy(DistributionRecipient::getDistributionId));
        Map<UUID, DistributionRecipient> mine = received
                ? recipientRepository.findByDistributionIdInAndRecipientUserId(ids, actor).stream()
                        .collect(Collectors.toMap(DistributionRecipient::getDistributionId, Function.identity()))
                : Map.of();
        Map<UUID, String> senderNames = names(distributions.stream().map(Distribution::getSenderUserId)
                .collect(Collectors.toSet()));
        return distributions.stream().map(distribution -> {
            DistributionRecipient own = mine.get(distribution.getId());
            return summary(distribution, senderNames.get(distribution.getSenderUserId()),
                    itemsByDistribution.getOrDefault(distribution.getId(), List.of()).size(),
                    recipientsByDistribution.getOrDefault(distribution.getId(), List.of()), own,
                    !received);
        }).toList();
    }

    private static DistributionSummaryResponse summary(Distribution distribution, String senderName, int itemCount,
                                                       List<DistributionRecipient> recipients,
                                                       DistributionRecipient own, boolean senderBox) {
        int readCount = (int) recipients.stream()
                .filter(recipient -> recipient.getRecipientStatus() == RecipientStatus.READ)
                .count();
        boolean canRevoke = senderBox && distribution.getDistributionStatus().isActive()
                && recipients.stream().anyMatch(recipient -> recipient.getRecipientStatus().isActive());
        return new DistributionSummaryResponse(distribution.getId(), distribution.getDistributionCode(),
                distribution.getTitle(), distribution.getSenderUserId(), senderName,
                distribution.getDistributionStatus().name(), distribution.getSentAt(), distribution.getExpiresAt(),
                itemCount, recipients.size(), readCount,
                own == null ? null : own.getRecipientStatus().name(),
                own == null ? null : recipientStatusAt(own), canRevoke);
    }

    private static Instant recipientStatusAt(DistributionRecipient recipient) {
        return switch (recipient.getRecipientStatus()) {
            case READ -> recipient.getReadAt();
            case REVOKED -> recipient.getRevokedAt();
            case EXPIRED -> recipient.getExpiresAt();
            case SENT -> recipient.getSentAt();
        };
    }

    @Transactional
    public DistributionDetailResponse detail(UUID actor, UUID distributionId) {
        Instant now = Instant.now(clock);
        expireDue(now);
        Distribution distribution = distributionRepository.findById(distributionId)
                .orElseThrow(() -> ApiException.notFound("Distribusi tidak ditemukan."));
        List<DistributionRecipient> recipients = recipientRepository
                .findByDistributionIdOrderBySentAtAsc(distributionId);
        DistributionRecipient own = recipients.stream()
                .filter(recipient -> recipient.getRecipientUserId().equals(actor))
                .findFirst().orElse(null);
        boolean sender = distribution.getSenderUserId().equals(actor);
        boolean canModerate = authorizationService.hasGlobalCapability(actor, PermissionCodes.DISTRIBUTION_REVOKE);
        if (!sender && own == null && !canModerate) {
            throw ApiException.notFound("Distribusi tidak ditemukan.");
        }
        if (own != null && own.getRecipientStatus() == RecipientStatus.SENT) {
            own.markRead(now);
            eventRepository.save(new DistributionEvent(UUID.randomUUID(), distributionId, own.getId(), actor,
                    DistributionEventType.READ, "Membuka detail distribusi.", now));
        }

        Map<UUID, String> senderNames = names(Set.of(distribution.getSenderUserId()));
        List<DistributionItem> items = itemRepository.findByDistributionIdOrderByCreatedAtAsc(distributionId);
        Map<UUID, Document> documents = documentRepository.findAllById(items.stream()
                .map(DistributionItem::getDocumentId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Document::getId, Function.identity()));
        Map<UUID, DocumentVersion> versions = versionRepository.findAllById(items.stream()
                .map(DistributionItem::getDocumentVersionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(DocumentVersion::getId, Function.identity()));
        Map<UUID, String> permissionCodes = permissionRepository.findAllById(items.stream()
                .map(DistributionItem::getPermissionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Permission::getId, Permission::getPermissionCode));
        Map<UUID, DocumentContext> contexts = loadContexts(documents.values());
        DocumentAccessService.ActorContext actorContext = documentAccessService.context(actor);
        List<DistributionItemResponse> itemResponses = items.stream().map(item -> {
            Document document = documents.get(item.getDocumentId());
            DocumentVersion version = versions.get(item.getDocumentVersionId());
            DocumentContext context = contexts.get(item.getDocumentId());
            boolean canOpen = document != null && context != null
                    && documentAccessService.canView(actorContext, document, context.documentGrants(),
                    context.folderGrants(), context.workspaceGrants());
            return new DistributionItemResponse(item.getDocumentId(),
                    document == null ? null : document.getTitle(),
                    document == null ? null : document.getDocumentCode(),
                    item.getDocumentVersionId(),
                    version == null ? null : version.getVersionNumber(),
                    version == null ? null : version.getVersionLabel(),
                    DistributionAccessLevel.fromItemPermission(
                            permissionCodes.getOrDefault(item.getPermissionId(), "")).name(),
                    canOpen);
        }).toList();

        Set<UUID> eventUserIds = new HashSet<>();
        List<DistributionEvent> events = eventRepository.findByDistributionIdOrderByOccurredAtAsc(distributionId);
        events.forEach(event -> {
            if (event.getActorUserId() != null) {
                eventUserIds.add(event.getActorUserId());
            }
        });
        recipients.forEach(recipient -> eventUserIds.add(recipient.getRecipientUserId()));
        Map<UUID, String> userNames = names(eventUserIds);
        List<DistributionRecipientResponse> recipientResponses = recipients.stream()
                .map(recipient -> new DistributionRecipientResponse(recipient.getId(),
                        recipient.getRecipientUserId(), userNames.get(recipient.getRecipientUserId()),
                        recipient.getRecipientStatus().name(), recipient.getSentAt(), recipient.getReadAt(),
                        recipient.getRevokedAt(), recipient.getExpiresAt()))
                .toList();
        List<DistributionEventResponse> eventResponses = events.stream()
                .map(event -> new DistributionEventResponse(event.getId(), event.getEventType().name(),
                        event.getDistributionRecipientId(),
                        recipientName(recipients, event.getDistributionRecipientId(), userNames),
                        event.getActorUserId(),
                        event.getActorUserId() == null ? null : userNames.get(event.getActorUserId()),
                        event.getEventNote(), event.getOccurredAt()))
                .toList();

        DistributionSummaryResponse summary = summary(distribution,
                senderNames.get(distribution.getSenderUserId()), items.size(), recipients, own,
                distribution.getSenderUserId().equals(actor) || canModerate);
        return new DistributionDetailResponse(summary, distribution.getMessage(), itemResponses,
                recipientResponses, eventResponses);
    }

    private static String recipientName(List<DistributionRecipient> recipients, UUID recipientId,
                                        Map<UUID, String> userNames) {
        if (recipientId == null) {
            return null;
        }
        return recipients.stream()
                .filter(recipient -> recipient.getId().equals(recipientId))
                .findFirst()
                .map(recipient -> userNames.get(recipient.getRecipientUserId()))
                .orElse(null);
    }

    // ---------------------------------------------------------------- revoke

    @Transactional
    public DistributionDetailResponse revoke(UUID actor, UUID distributionId) {
        Instant now = Instant.now(clock);
        expireDue(now);
        Distribution distribution = distributionRepository.findById(distributionId)
                .orElseThrow(() -> ApiException.notFound("Distribusi tidak ditemukan."));
        requireModeration(actor, distribution);
        if (!distribution.getDistributionStatus().isActive()) {
            throw ApiException.conflict("Distribusi tidak dapat dicabut pada status "
                    + distribution.getDistributionStatus().name() + ".");
        }
        for (DistributionRecipient recipient : recipientRepository
                .findByDistributionIdOrderBySentAtAsc(distributionId)) {
            if (recipient.getRecipientStatus().isActive()) {
                revokeRecipientInternal(actor, distribution, recipient, now);
            }
        }
        distribution.revoke(now);
        auditService.record(actor, "DISTRIBUTION_REVOKED", "DISTRIBUTION", distributionId, "SUCCESS",
                Map.of("distributionCode", distribution.getDistributionCode()));
        return detail(actor, distributionId);
    }

    @Transactional
    public DistributionDetailResponse revokeRecipient(UUID actor, UUID distributionId, UUID recipientId) {
        Instant now = Instant.now(clock);
        expireDue(now);
        Distribution distribution = distributionRepository.findById(distributionId)
                .orElseThrow(() -> ApiException.notFound("Distribusi tidak ditemukan."));
        requireModeration(actor, distribution);
        if (!distribution.getDistributionStatus().isActive()) {
            throw ApiException.conflict("Distribusi tidak dapat diubah pada status "
                    + distribution.getDistributionStatus().name() + ".");
        }
        DistributionRecipient recipient = recipientRepository.findById(recipientId)
                .filter(candidate -> candidate.getDistributionId().equals(distributionId))
                .orElseThrow(() -> ApiException.notFound("Penerima distribusi tidak ditemukan."));
        if (!recipient.getRecipientStatus().isActive()) {
            throw ApiException.conflict("Akses penerima ini sudah tidak aktif.");
        }
        revokeRecipientInternal(actor, distribution, recipient, now);
        boolean anyActive = recipientRepository.findByDistributionIdOrderBySentAtAsc(distributionId).stream()
                .anyMatch(candidate -> candidate.getRecipientStatus().isActive());
        if (anyActive) {
            distribution.markPartiallyRevoked(now);
        } else {
            distribution.revoke(now);
        }
        auditService.record(actor, "DISTRIBUTION_RECIPIENT_REVOKED", "DISTRIBUTION", distributionId, "SUCCESS",
                Map.of("distributionCode", distribution.getDistributionCode(),
                        "recipientUserId", recipient.getRecipientUserId().toString()));
        return detail(actor, distributionId);
    }

    private void requireModeration(UUID actor, Distribution distribution) {
        if (actor.equals(distribution.getSenderUserId())) {
            return;
        }
        if (!authorizationService.hasGlobalCapability(actor, PermissionCodes.DISTRIBUTION_REVOKE)) {
            throw ApiException.notFound("Distribusi tidak ditemukan.");
        }
    }

    private void revokeRecipientInternal(UUID actor, Distribution distribution,
                                         DistributionRecipient recipient, Instant now) {
        for (DistributionAccessGrant link : accessGrantRepository.findByDistributionRecipientId(recipient.getId())) {
            authorizationService.revokeGrant(actor, link.getResourcePermissionId());
        }
        recipient.revoke(now);
        eventRepository.save(new DistributionEvent(UUID.randomUUID(), distribution.getId(), recipient.getId(),
                actor, DistributionEventType.REVOKED, "Akses distribusi dicabut.", now));
    }

    // --------------------------------------------------------------- helpers

    private void expireDue(Instant now) {
        distributionRepository.expireDue(EnumSet.of(DistributionStatus.SENT, DistributionStatus.PARTIALLY_REVOKED),
                DistributionStatus.EXPIRED, now);
        recipientRepository.expireDue(EnumSet.of(RecipientStatus.SENT, RecipientStatus.READ),
                RecipientStatus.EXPIRED, now);
    }

    private Map<UUID, String> names(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getDisplayName));
    }

    private record DocumentContext(Document document, List<ResourcePermission> documentGrants,
                                   List<ResourcePermission> folderGrants,
                                   List<ResourcePermission> workspaceGrants) {
    }

    private Map<UUID, DocumentContext> loadContexts(Collection<Document> documents) {
        if (documents.isEmpty()) {
            return Map.of();
        }
        Set<UUID> documentIds = documents.stream().map(Document::getId).collect(Collectors.toSet());
        Set<UUID> folderIds = documents.stream().map(Document::getFolderId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> workspaceIds = documents.stream().map(Document::getWorkspaceId).collect(Collectors.toSet());
        Map<UUID, List<ResourcePermission>> documentGrants = authorizationService
                .activeResourcePermissions(ResourceType.DOCUMENT, documentIds);
        Map<UUID, List<ResourcePermission>> folderGrants = folderGrantResolver.grantsFor(folderIds);
        Map<UUID, List<ResourcePermission>> workspaceGrants = authorizationService
                .activeResourcePermissions(ResourceType.WORKSPACE, workspaceIds);
        Map<UUID, DocumentContext> contexts = new LinkedHashMap<>();
        for (Document document : documents) {
            List<ResourcePermission> folder = document.getFolderId() == null ? List.of()
                    : folderGrants.getOrDefault(document.getFolderId(), List.of());
            contexts.put(document.getId(), new DocumentContext(document,
                    documentGrants.getOrDefault(document.getId(), List.of()), folder,
                    workspaceGrants.getOrDefault(document.getWorkspaceId(), List.of())));
        }
        return contexts;
    }
}
