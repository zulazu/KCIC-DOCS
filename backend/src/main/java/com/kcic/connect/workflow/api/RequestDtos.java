package com.kcic.connect.workflow.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RequestDtos {

    public record CreateRequestRequest(
            @NotBlank(message = "Tipe request wajib dipilih.") String requestType,
            @NotBlank(message = "Judul request wajib diisi.") @Size(max = 200) String title,
            @Size(max = 4000) String description,
            @NotBlank(message = "Alasan request wajib diisi.") @Size(max = 4000) String reason,
            UUID targetDocumentId,
            UUID targetOwnerUserId,
            UUID targetOrganizationUnitId,
            LocalDate neededByDate) {
    }

    public record DecisionRequest(@Size(max = 2000) String comment) {
    }

    public record FulfillRequest(
            @NotBlank(message = "Tipe pemenuhan wajib dipilih.") String fulfillmentType,
            List<UUID> documentIds,
            String externalUrl,
            @Size(max = 200) String linkTitle,
            @Size(max = 2000) String note) {
    }

    public record ApprovalResponse(UUID id, UUID approverUserId, String approverName, String decision,
                                   String decisionReason, Instant assignedAt, Instant decidedAt) {
    }

    public record FulfillmentItemResponse(UUID documentId, String documentTitle, UUID documentVersionId) {
    }

    public record FulfillmentResponse(UUID id, String type, UUID fulfilledByUserId, String fulfilledByName,
                                      String note, String externalUrl, String externalLinkTitle,
                                      Instant fulfilledAt, List<FulfillmentItemResponse> items) {
    }

    public record HistoryResponse(UUID id, UUID actorUserId, String actorName, String fromStatus,
                                  String toStatus, String comment, Instant occurredAt) {
    }

    public record RequestSummaryResponse(
            UUID id,
            String requestCode,
            String requestType,
            String title,
            UUID requesterUserId,
            String requesterName,
            UUID assignedToUserId,
            String assignedToName,
            UUID targetDocumentId,
            String targetDocumentTitle,
            UUID targetOrganizationUnitId,
            String targetOrganizationUnitName,
            String status,
            LocalDate neededByDate,
            Instant submittedAt,
            Instant createdAt,
            Instant updatedAt,
            boolean canApprove,
            boolean canReject,
            boolean canFulfill,
            boolean canCancel,
            boolean canClose) {
    }

    public record RequestDetailResponse(
            RequestSummaryResponse summary,
            String description,
            String reason,
            UUID targetOwnerUserId,
            String targetOwnerName,
            List<ApprovalResponse> approvals,
            List<FulfillmentResponse> fulfillments,
            List<HistoryResponse> history) {
    }

    public record RequestOptionsResponse(List<RequestTypeOption> requestTypes) {
    }

    public record RequestTypeOption(String value, String label, boolean requiresDocument) {
    }
}
