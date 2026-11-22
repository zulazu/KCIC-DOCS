package com.kcic.connect.distribution.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DistributionDtos {

    public record CreateDistributionRequest(
            @NotBlank(message = "Judul distribusi wajib diisi.") @Size(max = 200) String title,
            @Size(max = 4000) String message,
            Instant expiresAt,
            List<UUID> recipientUserIds,
            List<UUID> recipientOrganizationUnitIds,
            @NotNull(message = "Dokumen distribusi wajib dipilih.")
            @Size(max = 50, message = "Maksimal 50 dokumen per distribusi.")
            List<@Valid DistributionItemRequest> items) {
    }

    public record DistributionItemRequest(
            @NotNull(message = "Dokumen wajib dipilih.") UUID documentId,
            @NotBlank(message = "Tingkat akses wajib dipilih.") String accessLevel) {
    }

    public record DistributionSummaryResponse(
            UUID id,
            String distributionCode,
            String title,
            UUID senderUserId,
            String senderName,
            String status,
            Instant sentAt,
            Instant expiresAt,
            int itemCount,
            int recipientCount,
            int readCount,
            String recipientStatus,
            Instant recipientStatusAt,
            boolean canRevoke) {
    }

    public record DistributionItemResponse(
            UUID documentId,
            String documentTitle,
            String documentCode,
            UUID documentVersionId,
            Integer versionNumber,
            String versionLabel,
            String accessLevel,
            boolean canOpen) {
    }

    public record DistributionRecipientResponse(
            UUID id,
            UUID userId,
            String userName,
            String status,
            Instant sentAt,
            Instant readAt,
            Instant revokedAt,
            Instant expiresAt) {
    }

    public record DistributionEventResponse(
            UUID id,
            String eventType,
            UUID recipientId,
            String recipientName,
            UUID actorUserId,
            String actorName,
            String note,
            Instant occurredAt) {
    }

    public record DistributionDetailResponse(
            DistributionSummaryResponse summary,
            String message,
            List<DistributionItemResponse> items,
            List<DistributionRecipientResponse> recipients,
            List<DistributionEventResponse> events) {
    }
}
