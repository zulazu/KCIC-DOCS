package com.kcic.connect.workflow.application;

import com.kcic.connect.audit.application.AuditService;
import com.kcic.connect.identity.domain.User;
import com.kcic.connect.identity.infrastructure.UserRepository;
import com.kcic.connect.notification.application.NotificationService;
import com.kcic.connect.shared.api.ApiException;
import com.kcic.connect.workflow.domain.ApprovalDecision;
import com.kcic.connect.workflow.domain.DocumentRequest;
import com.kcic.connect.workflow.domain.FulfillmentType;
import com.kcic.connect.workflow.domain.RequestFulfillment;
import com.kcic.connect.workflow.domain.RequestFulfillmentItem;
import com.kcic.connect.workflow.domain.RequestHistory;
import com.kcic.connect.workflow.domain.RequestStatus;
import com.kcic.connect.workflow.infrastructure.DocumentRequestRepository;
import com.kcic.connect.workflow.infrastructure.RequestApprovalRepository;
import com.kcic.connect.workflow.infrastructure.RequestFulfillmentItemRepository;
import com.kcic.connect.workflow.infrastructure.RequestFulfillmentRepository;
import com.kcic.connect.workflow.infrastructure.RequestHistoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a fulfillment after the file has already been stored as a new document
 * version. Kept separate from RequestService so the upload storage step is not
 * wrapped by a transaction that would claim success before the object exists.
 */
@Component
public class RequestFulfillmentRecorder {

    private static final Set<RequestStatus> FULFILLABLE =
            Set.of(RequestStatus.APPROVED, RequestStatus.IN_PROGRESS);

    private final DocumentRequestRepository requestRepository;
    private final RequestApprovalRepository approvalRepository;
    private final RequestFulfillmentRepository fulfillmentRepository;
    private final RequestFulfillmentItemRepository fulfillmentItemRepository;
    private final RequestHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public RequestFulfillmentRecorder(DocumentRequestRepository requestRepository,
                                      RequestApprovalRepository approvalRepository,
                                      RequestFulfillmentRepository fulfillmentRepository,
                                      RequestFulfillmentItemRepository fulfillmentItemRepository,
                                      RequestHistoryRepository historyRepository,
                                      UserRepository userRepository,
                                      NotificationService notificationService,
                                      AuditService auditService,
                                      Clock clock) {
        this.requestRepository = requestRepository;
        this.approvalRepository = approvalRepository;
        this.fulfillmentRepository = fulfillmentRepository;
        this.fulfillmentItemRepository = fulfillmentItemRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public void recordUploaded(UUID actor, UUID requestId, UUID documentId, UUID documentVersionId, String note) {
        DocumentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("Request tidak ditemukan."));
        if (!FULFILLABLE.contains(request.getRequestStatus())) {
            throw ApiException.conflict("Request berstatus " + request.getRequestStatus()
                    + " tidak dapat dipenuhi.");
        }
        Instant now = Instant.now(clock);
        UUID fulfillmentId = UUID.randomUUID();
        fulfillmentRepository.save(new RequestFulfillment(fulfillmentId, requestId,
                FulfillmentType.DOCUMENT_UPLOADED, actor, note, null, null, now));
        fulfillmentItemRepository.save(new RequestFulfillmentItem(UUID.randomUUID(), fulfillmentId, documentId,
                documentVersionId, now));
        RequestStatus from = request.getRequestStatus();
        if (from == RequestStatus.APPROVED) {
            request.transitionTo(RequestStatus.IN_PROGRESS, now);
            historyRepository.save(new RequestHistory(UUID.randomUUID(), requestId, actor, from,
                    RequestStatus.IN_PROGRESS, "Pemenuhan diproses.", Map.of(), now));
            from = RequestStatus.IN_PROGRESS;
        }
        request.transitionTo(RequestStatus.FULFILLED, now);
        approvalRepository.findFirstByRequestIdAndDecisionOrderByCreatedAtAsc(requestId, ApprovalDecision.PENDING)
                .ifPresent(approval -> approval.decide(ApprovalDecision.APPROVED, "Dipenuhi oleh approver.", now));
        historyRepository.save(new RequestHistory(UUID.randomUUID(), requestId, actor, from,
                RequestStatus.FULFILLED, "Dokumen hasil pemenuhan diunggah.", Map.of(), now));
        auditService.record(actor, "REQUEST_FULFILLED", "DOCUMENT_REQUEST", requestId, "SUCCESS",
                Map.of("requestCode", request.getRequestCode(),
                        "fulfillmentType", FulfillmentType.DOCUMENT_UPLOADED.name(), "itemCount", "1"));
        String actorName = userRepository.findById(actor).map(User::getDisplayName).orElse("Approver");
        notificationService.create(request.getRequesterUserId(), "REQUEST_FULFILLED",
                "Request " + request.getRequestCode() + " dipenuhi",
                actorName + " mengunggah dokumen pemenuhan.", "REQUEST", requestId,
                Map.of("requestCode", request.getRequestCode()));
    }
}
