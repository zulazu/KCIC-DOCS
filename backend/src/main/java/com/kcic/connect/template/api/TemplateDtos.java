package com.kcic.connect.template.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TemplateDtos {

    public record TemplateSummaryResponse(
            UUID id,
            String templateCode,
            String templateName,
            String description,
            String status,
            UUID documentId,
            String documentCode,
            String documentTitle,
            String documentStatus,
            UUID categoryId,
            String categoryName,
            UUID ownerUserId,
            String ownerUserName,
            UUID ownerOrganizationUnitId,
            String ownerOrganizationUnitName,
            UUID currentPublishedVersionId,
            Integer currentPublishedVersionNumber,
            String currentPublishedVersionLabel,
            String contentSource,
            Instant publishedAt,
            String publishedByName,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record TemplateVersionResponse(
            UUID id,
            int versionNumber,
            String versionLabel,
            String contentSource,
            String status,
            String changeSummary,
            Instant publishedAt,
            boolean currentlyPublished) {
    }

    public record TemplatePermissions(
            boolean canEdit,
            boolean canPublish,
            boolean canArchive,
            boolean canViewDocument,
            boolean canDownloadDocument) {
    }

    public record TemplateDetailResponse(
            TemplateSummaryResponse summary,
            String documentDescription,
            LocalDate effectiveDate,
            LocalDate reviewDate,
            LocalDate expiryDate,
            Instant documentUpdatedAt,
            String currentVersionChangeSummary,
            List<TemplateVersionResponse> versionHistory,
            boolean versionHistoryVisible,
            long usageCount,
            long viewCount,
            long downloadCount,
            long copyCount,
            TemplatePermissions permissions) {
    }

    public record TemplateUsageResponse(
            UUID templateId,
            String usageType,
            int versionNumber,
            Instant occurredAt) {
    }

    public record CreateTemplateRequest(
            @NotNull(message = "Dokumen sumber wajib dipilih.") UUID documentId,
            @NotBlank(message = "Kode template wajib diisi.") @Size(max = 80) String templateCode,
            @NotBlank(message = "Nama template wajib diisi.") @Size(max = 180) String templateName,
            @Size(max = 4000) String description) {
    }

    public record UpdateTemplateRequest(
            @NotBlank(message = "Kode template wajib diisi.") @Size(max = 80) String templateCode,
            @NotBlank(message = "Nama template wajib diisi.") @Size(max = 180) String templateName,
            @Size(max = 4000) String description) {
    }

    public record PublishTemplateRequest(UUID versionId) {
    }

    public record RecordTemplateUsageRequest(
            @NotBlank(message = "Jenis penggunaan wajib diisi.") String usageType) {
    }

    private TemplateDtos() {
    }
}
