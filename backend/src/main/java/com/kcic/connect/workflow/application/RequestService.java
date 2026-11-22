package com.kcic.connect.workflow.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.GrantSource;
import com.kcic.connect.authorization.domain.PermissionCodes;
import com.kcic.connect.authorization.domain.PrincipalType;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.notification.application.NotificationService;
import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.infrastructure.OrganizationUnitRepository;
import com.kcic.connect.repository.application.DocumentService;
import com.kcic.connect.repository.application.DocumentVersionService;
import com.kcic.connect.repository.api.DocumentDtos.DocumentVersionResponse;
import com.kcic.connect.repository.domain.DocumentVersion;
import com.kcic.connect.repository.infrastructure.DocumentRepository;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.shared.api.PageResponse;
import com.kcic.connect.workflow.api.RequestDtos.ApprovalResponse;
import com.kcic.connect.workflow.api.RequestDtos.CreateRequestRequest;
import com.kcic.connect.workflow.api.RequestDtos.FulfillRequest;
import com.kcic.connect.workflow.api.RequestDtos.FulfillmentItemResponse;
import com.kcic.connect.workflow.api.RequestDtos.FulfillmentResponse;
import com.kcic.connect.workflow.api.RequestDtos.HistoryResponse;
import com.kcic.connect.workflow.api.RequestDtos.RequestDetailResponse;
import com.kcic.connect.workflow.api.RequestDtos.RequestSummaryResponse;
import com.kcic.connect.workflow.domain.ApprovalDecision;
import com.kcic.connect.workflow.domain.DocumentRequest;
import com.kcic.connect.workflow.domain.FulfillmentType;
import com.kcic.connect.workflow.domain.RequestApproval;
import com.kcic.connect.workflow.domain.RequestFulfillment;
import com.kcic.connect.workflow.domain.RequestFulfillmentItem;
import com.kcic.connect.workflow.domain.RequestHistory;
import com.kcic.connect.workflow.domain.RequestStatus;
import com.kcic.connect.workflow.domain.RequestType;
import com.kcic.connect.workflow.infrastructure.DocumentRequestRepository;
import com.kcic.connect.workflow.infrastructure.RequestApprovalRepository;
import com.kcic.connect.workflow.infrastructure.RequestFulfillmentItemRepository;
import com.kcic.connect.workflow.infrastructure.RequestFulfillmentRepository;
import com.kcic.connect.workflow.infrastructure.RequestHistoryRepository;
import jakarta.persistence.criteria.Predicate;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RequestService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<RequestStatus> AWAITING_DECISION =
            Set.of(RequestStatus.SUBMITTED, RequestStatus.UNDER_REVIEW);
    private static final Set<RequestStatus> FULFILLABLE =
            Set.of(RequestStatus.APPROVED, RequestStatus.IN_PROGRESS);

    private final DocumentRequestRepository requestRepository;
    private final RequestApprovalRepository approvalRepository;
    private final RequestFulfillmentRepository fulfillmentRepository;
    private final RequestFulfillmentItemRepository fulfillmentItemRepository;
    private final RequestHistoryRepository historyRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final DocumentService documentService;
    private final DocumentVersionService documentVersionService;
    private final RequestFulfillmentRecorder fulfillmentRecorder;
    private final RequestRoutingService routingService;
    private final RequestCodeGenerator codeGenerator;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public RequestService(DocumentRequestRepository requestRepository,
                          RequestApprovalRepository approvalRepository,
                          RequestFulfillmentRepository fulfillmentRepository,
                          RequestFulfillmentItemRepository fulfillmentItemRepository,
                          RequestHistoryRepository historyRepository,
                          DocumentRepository documentRepository,
                          UserRepository userRepository,
                          OrganizationUnitRepository organizationUnitRepository,
                          DocumentService documentService,
                          DocumentVersionService documentVersionService,
                          RequestFulfillmentRecorder fulfillmentRecorder,
                          RequestRoutingService routingService,
                          RequestCodeGenerator codeGenerator,
                          AuthorizationService authorizationService,
                          NotificationService notificationService,
                          AuditService auditService,
                          Clock clock) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.fulfillmentItemRepository = fulfillmentItemRepository;
        this.historyRepository = historyRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.documentService = documentService;
        this.documentVersionService = documentVersionService;
        this.fulfillmentRecorder = fulfillmentRecorder;
        this.routingService = routingService;
        this.codeGenerator = codeGenerator;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    // ------------------------------------------------------------- create

    @Transactional
    public RequestDetailResponse create(UUID actor, CreateRequestRequest payload) {
        authorizationService.requireCapability(actor, PermissionCodes.REQUEST_CREATE);
        RequestType type = RequestType.from(payload.requestType());
        if (type == null) {
            throw ApiException.validation("requestType", "Tipe request tidak dikenal.");
        }
        if (type == RequestType.ACCESS && payload.targetDocumentId() == null) {
            throw ApiException.validation("targetDocumentId", "Request akses wajib menyebutkan dokumen target.");
        }
        if (payload.neededByDate() != null && payload.neededByDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.validation("neededByDate", "Tanggal kebutuhan tidak boleh di masa lalu.");
        }
        RequestRoutingService.Routing routing = routingService.resolve(type, payload.targetDocumentId(),
                payload.targetOwnerUserId(), payload.targetOrganizationUnitId());
        Instant now = Instant.now(clock);
        String code = codeGenerator.nextCode();
        UUID requestId = UUID.randomUUID();
        DocumentRequest request = requestRepository.save(new DocumentRequest(requestId, code, type, actor,
                payload.title().trim(), trimToNull(payload.description()), payload.reason().trim(),
                payload.targetDocumentId(), routing.ownerUserId(),
                routing.ownerUnitId(), routing.approverUserId(), payload.neededByDate(),
                RequestStatus.SUBMITTED, now, now));
        approvalRepository.save(new RequestApproval(UUID.randomUUID(), requestId, routing.approverUserId(), now));
        history(requestId, actor, null, RequestStatus.SUBMITTED, "Request diajukan.", now);
        auditService.record(actor, "REQUEST_SUBMITTED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", code, "requestType", type.name(),
                        "assignedToUserId", routing.approverUserId().toString()));
        String requesterName = displayName(actor);
        String label = label(type);
        if (!routing.approverUserId().equals(actor)) {
            notificationService.create(routing.approverUserId(), "REQUEST_ASSIGNED",
                    "Request baru: " + code,
                    requesterName + " mengajukan " + label + ": " + request.getTitle(),
                    "REQUEST", requestId, Map.of("requestCode", code));
        }
        notificationService.create(actor, "REQUEST_SUBMITTED", "Request " + code + " terkirim",
                "Menunggu review dari " + displayName(routing.approverUserId()) + ".", "REQUEST", requestId,
                Map.of("requestCode", code));
        return detail(actor, requestId);
    }

    // --------------------------------------------------------------- list

    @Transactional(readOnly = true)
    public PageResponse<RequestSummaryResponse> list(UUID actor, String scope, String status, String type,
                                                     String keyword, int page, int size) {
        String normalizedScope = scope == null ? "own" : scope.trim().toLowerCase();
        boolean assignedScope = "assigned".equals(normalizedScope);
        if (!assignedScope && !"own".equals(normalizedScope)) {
            throw ApiException.validation("scope", "Scope harus own atau assigned.");
        }
        if (assignedScope) {
            requireAssignedViewer(actor);
        } else {
            authorizationService.requireCapability(actor, PermissionCodes.REQUEST_VIEW_OWN);
        }
        RequestStatus statusFilter = parseStatus(status);
        RequestType typeFilter = RequestType.from(type);
        if (type != null && !type.isBlank() && typeFilter == null) {
            throw ApiException.validation("requestType", "Tipe request tidak dikenal.");
        }
        String term = keyword == null ? "" : keyword.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        boolean seesEverything = assignedScope && seesAllRequests(actor);
        Set<UUID> units = assignedScope ? authorizationService.activeUnitIds(actor) : Set.of();
        Specification<DocumentRequest> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (assignedScope) {
                if (!seesEverything) {
                    List<Predicate> visibility = new ArrayList<>();
                    visibility.add(cb.equal(root.get("assignedToUserId"), actor));
                    if (!units.isEmpty()) {
                        visibility.add(root.get("targetOrganizationUnitId").in(units));
                    }
                    predicates.add(cb.or(visibility.toArray(Predicate[]::new)));
                }
            } else {
                predicates.add(cb.equal(root.get("requesterUserId"), actor));
            }
            if (statusFilter != null) {
                predicates.add(cb.equal(root.get("requestStatus"), statusFilter));
            }
            if (typeFilter != null) {
                predicates.add(cb.equal(root.get("requestType"), typeFilter));
            }
            if (!term.isEmpty()) {
                String like = "%" + term.toLowerCase() + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("requestCode")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<DocumentRequest> result = requestRepository.findAll(specification,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<RequestSummaryResponse> items = summaries(actor, result.getContent());
        return PageResponse.of(items, safePage, safeSize, result.getTotalElements());
    }

    // ------------------------------------------------------------- detail

    @Transactional(readOnly = true)
    public RequestDetailResponse detail(UUID actor, UUID requestId) {
        DocumentRequest request = load(requestId);
        if (!canView(actor, request)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk melihat request ini.");
        }
        List<ApprovalResponse> approvals = approvalRepository.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                .map(approval -> new ApprovalResponse(approval.getId(), approval.getApproverUserId(),
                        displayName(approval.getApproverUserId()), approval.getDecision().name(),
                        approval.getDecisionReason(), approval.getAssignedAt(), approval.getDecidedAt()))
                .toList();
        List<RequestFulfillment> fulfillments = fulfillmentRepository.findByRequestIdOrderByFulfilledAtAsc(requestId);
        Map<UUID, List<RequestFulfillmentItem>> itemsByFulfillment = new HashMap<>();
        if (!fulfillments.isEmpty()) {
            fulfillmentItemRepository.findByFulfillmentIdInOrderByCreatedAtAsc(
                            fulfillments.stream().map(RequestFulfillment::getId).toList())
                    .forEach(item -> itemsByFulfillment
                            .computeIfAbsent(item.getFulfillmentId(), key -> new ArrayList<>()).add(item));
        }
        Map<UUID, String> documentTitles = new HashMap<>();
        Set<UUID> documentIds = new HashSet<>();
        itemsByFulfillment.values().forEach(items -> items.forEach(item -> documentIds.add(item.getDocumentId())));
        if (!documentIds.isEmpty()) {
            documentRepository.findAllById(documentIds)
                    .forEach(document -> documentTitles.put(document.getId(), document.getTitle()));
        }
        List<FulfillmentResponse> fulfillmentResponses = fulfillments.stream()
                .map(fulfillment -> new FulfillmentResponse(fulfillment.getId(),
                        fulfillment.getFulfillmentType().name(), fulfillment.getFulfilledByUserId(),
                        displayName(fulfillment.getFulfilledByUserId()), fulfillment.getFulfillmentNote(),
                        fulfillment.getExternalUrl(), fulfillment.getExternalLinkTitle(),
                        fulfillment.getFulfilledAt(),
                        itemsByFulfillment.getOrDefault(fulfillment.getId(), List.of()).stream()
                                .map(item -> new FulfillmentItemResponse(item.getDocumentId(),
                                        documentTitles.get(item.getDocumentId()), item.getDocumentVersionId()))
                                .toList()))
                .toList();
        List<HistoryResponse> history = historyRepository.findByRequestIdOrderByOccurredAtAsc(requestId).stream()
                .map(entry -> new HistoryResponse(entry.getId(), entry.getActorUserId(),
                        entry.getActorUserId() == null ? null : displayName(entry.getActorUserId()),
                        entry.getFromStatus() == null ? null : entry.getFromStatus().name(),
                        entry.getToStatus().name(), entry.getComment(), entry.getOccurredAt()))
                .toList();
        RequestSummaryResponse summary = summaries(actor, List.of(request)).get(0);
        return new RequestDetailResponse(summary, request.getDescription(), request.getReason(),
                request.getTargetOwnerUserId(),
                request.getTargetOwnerUserId() == null ? null : displayName(request.getTargetOwnerUserId()),
                approvals, fulfillmentResponses, history);
    }

    // ----------------------------------------------------------- decisions

    @Transactional
    public RequestDetailResponse startReview(UUID actor, UUID requestId, String comment) {
        DocumentRequest request = load(requestId);
        requireApprover(actor, request);
        requireStatus(request, RequestStatus.UNDER_REVIEW);
        Instant now = Instant.now(clock);
        RequestStatus from = request.getRequestStatus();
        request.transitionTo(RequestStatus.UNDER_REVIEW, now);
        history(requestId, actor, from, RequestStatus.UNDER_REVIEW, trimToNull(comment), now);
        auditService.record(actor, "REQUEST_UNDER_REVIEW", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode()));
        return detail(actor, requestId);
    }

    @Transactional
    public RequestDetailResponse approve(UUID actor, UUID requestId, String comment) {
        DocumentRequest request = load(requestId);
        requireApprover(actor, request);
        requireStatus(request, RequestStatus.APPROVED);
        Instant now = Instant.now(clock);
        RequestStatus from = request.getRequestStatus();
        request.transitionTo(RequestStatus.APPROVED, now);
        decidePending(requestId, ApprovalDecision.APPROVED, trimToNull(comment), now);
        history(requestId, actor, from, RequestStatus.APPROVED, trimToNull(comment), now);
        auditService.record(actor, "REQUEST_APPROVED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode()));
        notificationService.create(request.getRequesterUserId(), "REQUEST_APPROVED",
                "Request " + request.getRequestCode() + " disetujui",
                displayName(actor) + " menyetujui request Anda.", "REQUEST", requestId,
                Map.of("requestCode", request.getRequestCode()));
        return detail(actor, requestId);
    }

    @Transactional
    public RequestDetailResponse reject(UUID actor, UUID requestId, String comment) {
        DocumentRequest request = load(requestId);
        requireApprover(actor, request);
        requireStatus(request, RequestStatus.REJECTED);
        String reason = trimToNull(comment);
        if (reason == null) {
            throw ApiException.validation("comment", "Alasan penolakan wajib diisi.");
        }
        Instant now = Instant.now(clock);
        RequestStatus from = request.getRequestStatus();
        request.transitionTo(RequestStatus.REJECTED, now);
        decidePending(requestId, ApprovalDecision.REJECTED, reason, now);
        history(requestId, actor, from, RequestStatus.REJECTED, reason, now);
        auditService.record(actor, "REQUEST_REJECTED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode()));
        notificationService.create(request.getRequesterUserId(), "REQUEST_REJECTED",
                "Request " + request.getRequestCode() + " ditolak",
                displayName(actor) + " menolak request Anda: " + reason, "REQUEST", requestId,
                Map.of("requestCode", request.getRequestCode()));
        return detail(actor, requestId);
    }

    @Transactional
    public RequestDetailResponse cancel(UUID actor, UUID requestId, String comment) {
        DocumentRequest request = load(requestId);
        boolean participant = request.getRequesterUserId().equals(actor) || actor.equals(request.getAssignedToUserId());
        if (!participant && !seesAllRequests(actor)) {
            throw ApiException.forbidden("Anda tidak memiliki izin membatalkan request ini.");
        }
        requireStatus(request, RequestStatus.CANCELLED);
        Instant now = Instant.now(clock);
        RequestStatus from = request.getRequestStatus();
        request.transitionTo(RequestStatus.CANCELLED, now);
        decidePending(requestId, ApprovalDecision.CANCELLED, trimToNull(comment), now);
        history(requestId, actor, from, RequestStatus.CANCELLED, trimToNull(comment), now);
        auditService.record(actor, "REQUEST_CANCELLED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode()));
        UUID recipient = request.getRequesterUserId().equals(actor)
                ? request.getAssignedToUserId() : request.getRequesterUserId();
        if (recipient != null && !recipient.equals(actor)) {
            notificationService.create(recipient, "REQUEST_CANCELLED",
                    "Request " + request.getRequestCode() + " dibatalkan",
                    displayName(actor) + " membatalkan request " + request.getRequestCode() + ".",
                    "REQUEST", requestId, Map.of("requestCode", request.getRequestCode()));
        }
        return detail(actor, requestId);
    }

    @Transactional
    public RequestDetailResponse close(UUID actor, UUID requestId, String comment) {
        DocumentRequest request = load(requestId);
        boolean participant = request.getRequesterUserId().equals(actor) || actor.equals(request.getAssignedToUserId());
        if (!participant && !seesAllRequests(actor)) {
            throw ApiException.forbidden("Anda tidak memiliki izin menutup request ini.");
        }
        requireStatus(request, RequestStatus.CLOSED);
        Instant now = Instant.now(clock);
        RequestStatus from = request.getRequestStatus();
        request.transitionTo(RequestStatus.CLOSED, now);
        history(requestId, actor, from, RequestStatus.CLOSED, trimToNull(comment), now);
        auditService.record(actor, "REQUEST_CLOSED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode()));
        return detail(actor, requestId);
    }

    @Transactional
    public RequestDetailResponse fulfill(UUID actor, UUID requestId, FulfillRequest payload) {
        DocumentRequest request = load(requestId);
        requireFulfiller(actor, request);
        if (!FULFILLABLE.contains(request.getRequestStatus())) {
            throw ApiException.conflict("Request berstatus " + request.getRequestStatus()
                    + " tidak dapat dipenuhi.");
        }
        FulfillmentType type = parseFulfillmentType(payload.fulfillmentType());
        if (type == FulfillmentType.DOCUMENT_UPLOADED) {
            throw ApiException.validation("fulfillmentType",
                    "Gunakan endpoint unggah versi dokumen untuk pemenuhan berupa file.");
        }
        Instant now = Instant.now(clock);
        UUID fulfillmentId = UUID.randomUUID();
        List<RequestFulfillmentItem> items = switch (type) {
            case ACCESS_GRANTED -> grantAccess(actor, request, fulfillmentId, now);
            case DOCUMENT_LINKED -> linkDocuments(actor, payload.documentIds(), fulfillmentId, now);
            case EXTERNAL_LINK_REGISTERED -> registerExternalLink(payload, fulfillmentId, now);
            case DOCUMENT_UPLOADED -> throw ApiException.validation("fulfillmentType",
                    "Tipe pemenuhan tidak valid.");
        };
        fulfillmentRepository.save(new RequestFulfillment(fulfillmentId, requestId, type, actor,
                trimToNull(payload.note()), trimToNull(payload.externalUrl()), trimToNull(payload.linkTitle()), now));
        fulfillmentItemRepository.saveAll(items);
        completeFulfillment(request, actor, now, type, items.size(), fulfillmentId);
        return detail(actor, requestId);
    }

    public RequestDetailResponse fulfillUpload(UUID actor, UUID requestId, UUID documentId, MultipartFile file,
                                               String note, String versionLabel) {
        DocumentRequest request = load(requestId);
        requireFulfiller(actor, request);
        if (!FULFILLABLE.contains(request.getRequestStatus())) {
            throw ApiException.conflict("Request berstatus " + request.getRequestStatus()
                    + " tidak dapat dipenuhi.");
        }
        DocumentVersionResponse version = documentVersionService.upload(actor, documentId, file, versionLabel,
                "Pemenuhan request " + request.getRequestCode());
        fulfillmentRecorder.recordUploaded(actor, requestId, documentId, version.id(), trimToNull(note));
        return detail(actor, requestId);
    }

    private List<RequestFulfillmentItem> grantAccess(UUID actor, DocumentRequest request, UUID fulfillmentId,
                                                     Instant now) {
        UUID documentId = request.getTargetDocumentId();
        if (documentId == null) {
            throw ApiException.validation("fulfillmentType",
                    "Request ini tidak memiliki dokumen target untuk diberi akses.");
        }
        UUID requester = request.getRequesterUserId();
        for (String code : List.of(PermissionCodes.DOCUMENT_VIEW, PermissionCodes.DOCUMENT_PREVIEW,
                PermissionCodes.DOCUMENT_DOWNLOAD)) {
            if (!authorizationService.hasActiveUserAllow(ResourceType.DOCUMENT, documentId, requester, code)) {
                authorizationService.grant(actor, ResourceType.DOCUMENT, documentId,
                        PrincipalType.USER, requester, code, AclEffect.ALLOW, GrantSource.REQUEST, null);
            }
        }
        UUID versionId = documentService.currentVersions(List.of(documentId)).stream()
                .map(DocumentVersion::getId).findFirst().orElse(null);
        return List.of(new RequestFulfillmentItem(UUID.randomUUID(), fulfillmentId, documentId, versionId, now));
    }

    private List<RequestFulfillmentItem> linkDocuments(UUID actor, List<UUID> documentIds, UUID fulfillmentId,
                                                       Instant now) {
        if (documentIds == null || documentIds.isEmpty()) {
            throw ApiException.validation("documentIds", "Pilih minimal satu dokumen.");
        }
        if (documentIds.size() > 20) {
            throw ApiException.validation("documentIds", "Maksimal 20 dokumen per pemenuhan.");
        }
        List<UUID> unique = documentIds.stream().distinct().toList();
        unique.forEach(documentId -> documentService.detail(actor, documentId));
        Map<UUID, UUID> currentVersions = new HashMap<>();
        documentService.currentVersions(unique)
                .forEach(version -> currentVersions.put(version.getDocumentId(), version.getId()));
        return unique.stream()
                .map(documentId -> new RequestFulfillmentItem(UUID.randomUUID(), fulfillmentId, documentId,
                        currentVersions.get(documentId), now))
                .toList();
    }

    private List<RequestFulfillmentItem> registerExternalLink(FulfillRequest payload, UUID fulfillmentId,
                                                              Instant now) {
        String rawUrl = trimToNull(payload.externalUrl());
        if (rawUrl == null) {
            throw ApiException.validation("externalUrl", "URL eksternal wajib diisi.");
        }
        if (rawUrl.length() > 2048) {
            throw ApiException.validation("externalUrl", "URL terlalu panjang.");
        }
        try {
            URI uri = URI.create(rawUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw ApiException.validation("externalUrl", "URL harus menggunakan HTTPS.");
            }
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("externalUrl", "URL tidak valid.");
        }
        return List.of();
    }

    private void completeFulfillment(DocumentRequest request, UUID actor, Instant now, FulfillmentType type,
                                     int itemCount, UUID fulfillmentId) {
        RequestStatus from = request.getRequestStatus();
        if (from == RequestStatus.APPROVED) {
            request.transitionTo(RequestStatus.IN_PROGRESS, now);
            history(request.getId(), actor, from, RequestStatus.IN_PROGRESS, "Pemenuhan diproses.", now);
            from = RequestStatus.IN_PROGRESS;
        }
        request.transitionTo(RequestStatus.FULFILLED, now);
        decidePending(request.getId(), ApprovalDecision.APPROVED, "Dipenuhi oleh approver.", now);
        history(request.getId(), actor, from, RequestStatus.FULFILLED, "Request dipenuhi.", now);
        auditService.record(actor, "REQUEST_FULFILLED", "DOCUMENT_REQUEST", request.getId(), "SUCCESS",
                Map.of("requestCode", request.getRequestCode(), "fulfillmentType", type.name(),
                        "itemCount", Integer.toString(itemCount)));
        notificationService.create(request.getRequesterUserId(), "REQUEST_FULFILLED",
                "Request " + request.getRequestCode() + " dipenuhi",
                displayName(actor) + " memenuhi request Anda (" + label(type) + ").", "REQUEST",
                request.getId(), Map.of("requestCode", request.getRequestCode()));
    }

    // --------------------------------------------------------------- guards

    private DocumentRequest load(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request tidak ditemukan."));
    }

    private void requireStatus(DocumentRequest request, RequestStatus target) {
        if (request.getRequestStatus() == target || !request.getRequestStatus().canTransitionTo(target)) {
            throw ApiException.conflict("Request berstatus " + request.getRequestStatus()
                    + " tidak dapat diubah menjadi " + target + ".");
        }
    }

    private void requireApprover(UUID actor, DocumentRequest request) {
        if (!approverEligible(actor, request)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk memutus request ini.");
        }
    }

    private void requireFulfiller(UUID actor, DocumentRequest request) {
        if (!fulfillerEligible(actor, request)) {
            throw ApiException.forbidden("Anda tidak memiliki izin untuk memenuhi request ini.");
        }
    }

    private boolean approverEligible(UUID actor, DocumentRequest request) {
        if (actor.equals(request.getAssignedToUserId())
                && authorizationService.hasCapability(actor, PermissionCodes.REQUEST_APPROVE)) {
            return true;
        }
        if (authorizationService.hasGlobalCapability(actor, PermissionCodes.REQUEST_APPROVE)) {
            return true;
        }
        Set<UUID> units = targetUnits(request);
        return !units.isEmpty()
                && authorizationService.allowsUnitCapability(actor, PermissionCodes.REQUEST_APPROVE, units);
    }

    private boolean fulfillerEligible(UUID actor, DocumentRequest request) {
        if (actor.equals(request.getAssignedToUserId())
                && authorizationService.hasCapability(actor, PermissionCodes.REQUEST_FULFILL)) {
            return true;
        }
        if (authorizationService.hasGlobalCapability(actor, PermissionCodes.REQUEST_FULFILL)) {
            return true;
        }
        Set<UUID> units = targetUnits(request);
        return !units.isEmpty()
                && authorizationService.allowsUnitCapability(actor, PermissionCodes.REQUEST_FULFILL, units);
    }

    private boolean canView(UUID actor, DocumentRequest request) {
        if (request.getRequesterUserId().equals(actor) || actor.equals(request.getAssignedToUserId())) {
            return true;
        }
        return seesAllRequests(actor) || approverEligible(actor, request) || fulfillerEligible(actor, request);
    }

    private void requireAssignedViewer(UUID actor) {
        Set<String> capabilities = authorizationService.capabilities(actor);
        if (!capabilities.contains(PermissionCodes.REQUEST_VIEW_ASSIGNED)
                && !capabilities.contains(PermissionCodes.REQUEST_APPROVE)
                && !capabilities.contains(PermissionCodes.REQUEST_FULFILL)) {
            throw ApiException.forbidden("Anda tidak memiliki izin melihat request yang ditugaskan.");
        }
    }

    private boolean seesAllRequests(UUID actor) {
        return authorizationService.hasGlobalCapability(actor, PermissionCodes.REQUEST_VIEW_ASSIGNED)
                || authorizationService.hasGlobalCapability(actor, PermissionCodes.REQUEST_APPROVE)
                || authorizationService.hasGlobalCapability(actor, PermissionCodes.REQUEST_FULFILL);
    }

    private static Set<UUID> targetUnits(DocumentRequest request) {
        return request.getTargetOrganizationUnitId() == null
                ? Set.of() : Set.of(request.getTargetOrganizationUnitId());
    }

    private void decidePending(UUID requestId, ApprovalDecision decision, String reason, Instant now) {
        approvalRepository.findFirstByRequestIdAndDecisionOrderByCreatedAtAsc(requestId, ApprovalDecision.PENDING)
                .ifPresent(approval -> approval.decide(decision, reason, now));
    }

    private void history(UUID requestId, UUID actor, RequestStatus from, RequestStatus to, String comment,
                         Instant now) {
        historyRepository.save(new RequestHistory(UUID.randomUUID(), requestId, actor, from, to, comment,
                Map.of(), now));
    }

    // ------------------------------------------------------------ responses

    private List<RequestSummaryResponse> summaries(UUID actor, List<DocumentRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        Set<UUID> userIds = new HashSet<>();
        Set<UUID> unitIds = new HashSet<>();
        Set<UUID> documentIds = new HashSet<>();
        for (DocumentRequest request : requests) {
            userIds.add(request.getRequesterUserId());
            if (request.getAssignedToUserId() != null) {
                userIds.add(request.getAssignedToUserId());
            }
            if (request.getTargetOrganizationUnitId() != null) {
                unitIds.add(request.getTargetOrganizationUnitId());
            }
            if (request.getTargetDocumentId() != null) {
                documentIds.add(request.getTargetDocumentId());
            }
        }
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> users.put(user.getId(), user));
        Map<UUID, String> units = new HashMap<>();
        organizationUnitRepository.findAllById(unitIds)
                .forEach(unit -> units.put(unit.getId(), unit.getUnitName()));
        Map<UUID, String> documents = new HashMap<>();
        documentRepository.findAllById(documentIds)
                .forEach(document -> documents.put(document.getId(), document.getTitle()));
        boolean seesEverything = seesAllRequests(actor);
        return requests.stream().map(request -> {
            boolean awaiting = AWAITING_DECISION.contains(request.getRequestStatus());
            boolean fulfillable = FULFILLABLE.contains(request.getRequestStatus());
            boolean isRequester = request.getRequesterUserId().equals(actor);
            boolean canApprove = awaiting && approverEligible(actor, request);
            boolean canFulfill = fulfillable && fulfillerEligible(actor, request);
            boolean terminal = request.getRequestStatus() == RequestStatus.FULFILLED
                    || request.getRequestStatus() == RequestStatus.REJECTED;
            boolean canCancel = isRequester && request.getRequestStatus().isOpen()
                    && request.getRequestStatus() != RequestStatus.DRAFT;
            boolean canClose = terminal && (isRequester || actor.equals(request.getAssignedToUserId())
                    || seesEverything);
            return new RequestSummaryResponse(request.getId(), request.getRequestCode(),
                    request.getRequestType().name(), request.getTitle(), request.getRequesterUserId(),
                    nameOf(users, request.getRequesterUserId()), request.getAssignedToUserId(),
                    nameOf(users, request.getAssignedToUserId()), request.getTargetDocumentId(),
                    documents.get(request.getTargetDocumentId()), request.getTargetOrganizationUnitId(),
                    units.get(request.getTargetOrganizationUnitId()), request.getRequestStatus().name(),
                    request.getNeededByDate(), request.getSubmittedAt(), request.getCreatedAt(),
                    request.getUpdatedAt(), canApprove, canApprove, canFulfill, canCancel, canClose);
        }).toList();
    }

    private String displayName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(User::getDisplayName).orElse(null);
    }

    private static String nameOf(Map<UUID, User> users, UUID userId) {
        if (userId == null) {
            return null;
        }
        User user = users.get(userId);
        return user == null ? null : user.getDisplayName();
    }

    private static String label(RequestType type) {
        return switch (type) {
            case ACCESS -> "akses dokumen";
            case DOCUMENT_DELIVERY -> "permintaan dokumen";
            case TEMPLATE -> "permintaan template";
        };
    }

    private static String label(FulfillmentType type) {
        return switch (type) {
            case ACCESS_GRANTED -> "akses diberikan";
            case DOCUMENT_LINKED -> "dokumen ditautkan";
            case DOCUMENT_UPLOADED -> "dokumen diunggah";
            case EXTERNAL_LINK_REGISTERED -> "tautan eksternal";
        };
    }

    private static RequestStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return RequestStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("status", "Status request tidak dikenal.");
        }
    }

    private static FulfillmentType parseFulfillmentType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.validation("fulfillmentType", "Tipe pemenuhan wajib dipilih.");
        }
        try {
            return FulfillmentType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw ApiException.validation("fulfillmentType", "Tipe pemenuhan tidak dikenal.");
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
