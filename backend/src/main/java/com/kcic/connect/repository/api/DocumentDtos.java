package com.kcic.connect.repository.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DocumentDtos {

    public record TagView(UUID id, String tagCode, String tagName) {
    }

    public record DocumentSummaryResponse(
            UUID id,
            String documentCode,
            String title,
            String status,
            UUID workspaceId,
            String workspaceName,
            UUID folderId,
            String folderName,
            UUID categoryId,
            String categoryName,
            UUID documentTypeId,
            String documentTypeName,
            UUID classificationId,
            String classificationName,
            int classificationRank,
            UUID ownerUserId,
            String ownerUserName,
            UUID ownerOrganizationUnitId,
            String ownerOrganizationUnitName,
            List<TagView> tags,
            Integer currentVersionNumber,
            String currentVersionLabel,
            String contentSource,
            Instant updatedAt,
            boolean favorited) {
    }

    public record DocumentVersionResponse(
            UUID id,
            int versionNumber,
            String versionLabel,
            String contentSource,
            String status,
            String changeSummary,
            UUID createdBy,
            String createdByName,
            Instant createdAt,
            Instant publishedAt,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            String checksumSha256,
            String externalUrl,
            String externalHost,
            boolean canPreview,
            boolean canDownload) {
    }

    public record OwnerHistoryResponse(
            UUID id,
            UUID ownerUserId,
            String ownerUserName,
            UUID ownerOrganizationUnitId,
            String ownerOrganizationUnitName,
            UUID changedBy,
            String changedByName,
            String changeReason,
            Instant validFrom,
            Instant validUntil) {
    }

    public record DocumentPermissions(
            boolean canEdit,
            boolean canUpload,
            boolean canCreateVersion,
            boolean canArchive,
            boolean canRestore,
            boolean canPreview,
            boolean canDownload,
            boolean canManageAccess) {
    }

    public record DocumentDetailResponse(
            DocumentSummaryResponse summary,
            String description,
            LocalDate effectiveDate,
            LocalDate reviewDate,
            LocalDate expiryDate,
            Instant createdAt,
            UUID createdBy,
            String createdByName,
            DocumentVersionResponse currentVersion,
            List<OwnerHistoryResponse> ownerHistory,
            long versionCount,
            DocumentPermissions permissions) {
    }

    public record CreateDocumentRequest(
            @Size(max = 100, message = "Kode dokumen maksimal 100 karakter.") String documentCode,
            @NotBlank(message = "Judul dokumen wajib diisi.") @Size(max = 240) String title,
            @Size(max = 4000) String description,
            UUID folderId,
            @NotNull(message = "Kategori wajib dipilih.") UUID categoryId,
            @NotNull(message = "Jenis dokumen wajib dipilih.") UUID documentTypeId,
            @NotNull(message = "Klasifikasi wajib dipilih.") UUID classificationId,
            UUID ownerUserId,
            UUID ownerOrganizationUnitId,
            @Size(max = 20, message = "Maksimal 20 tag.") List<UUID> tagIds,
            LocalDate effectiveDate,
            LocalDate reviewDate,
            LocalDate expiryDate) {
    }

    public record UpdateDocumentRequest(
            @Size(max = 100, message = "Kode dokumen maksimal 100 karakter.") String documentCode,
            @NotBlank(message = "Judul dokumen wajib diisi.") @Size(max = 240) String title,
            @Size(max = 4000) String description,
            UUID folderId,
            @NotNull(message = "Kategori wajib dipilih.") UUID categoryId,
            @NotNull(message = "Jenis dokumen wajib dipilih.") UUID documentTypeId,
            @NotNull(message = "Klasifikasi wajib dipilih.") UUID classificationId,
            UUID ownerUserId,
            UUID ownerOrganizationUnitId,
            @Size(max = 20, message = "Maksimal 20 tag.") List<UUID> tagIds,
            LocalDate effectiveDate,
            LocalDate reviewDate,
            LocalDate expiryDate,
            @Size(max = 500, message = "Alasan perubahan owner maksimal 500 karakter.")
            String ownerChangeReason) {
    }

    public record ExternalLinkRequest(
            @NotBlank(message = "URL wajib diisi.") @Size(max = 2000) String url,
            @Size(max = 240) String linkTitle,
            @Size(max = 1000) String changeSummary,
            @Size(max = 60) String versionLabel) {
    }

    public record ExternalLinkResponse(String url, String host, String warning) {
    }

    public record ActivityResponse(
            UUID id,
            String actionCode,
            String outcome,
            UUID actorUserId,
            String actorName,
            Instant occurredAt,
            Map<String, Object> metadata) {
    }

    private DocumentDtos() {
    }
}
