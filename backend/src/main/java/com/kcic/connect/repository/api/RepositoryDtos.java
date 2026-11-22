package com.kcic.connect.repository.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class RepositoryDtos {

    public record WorkspaceResponse(
            UUID id,
            String workspaceCode,
            String workspaceName,
            String description,
            UUID ownerOrganizationUnitId,
            String ownerOrganizationUnitName,
            UUID ownerUserId,
            String status,
            long activeFolderCount,
            boolean canManage,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateWorkspaceRequest(
            @NotBlank(message = "Kode workspace wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,80}",
                    message = "Kode workspace 2-80 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String workspaceCode,
            @NotBlank(message = "Nama workspace wajib diisi.") @Size(max = 160) String workspaceName,
            @Size(max = 4000) String description,
            @jakarta.validation.constraints.NotNull(message = "Unit pemilik wajib dipilih.")
            UUID ownerOrganizationUnitId,
            UUID ownerUserId) {
    }

    public record UpdateWorkspaceRequest(
            @NotBlank(message = "Nama workspace wajib diisi.") @Size(max = 160) String workspaceName,
            @Size(max = 4000) String description,
            @jakarta.validation.constraints.NotNull(message = "Unit pemilik wajib dipilih.")
            UUID ownerOrganizationUnitId,
            UUID ownerUserId,
            @NotBlank(message = "Status workspace wajib diisi.") String status) {
    }

    public record FolderResponse(
            UUID id,
            UUID workspaceId,
            UUID parentFolderId,
            String folderName,
            String description,
            String status,
            boolean canManage,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateFolderRequest(
            UUID parentFolderId,
            @NotBlank(message = "Nama folder wajib diisi.") @Size(max = 160) String folderName,
            @Size(max = 4000) String description) {
    }

    public record UpdateFolderRequest(
            UUID parentFolderId,
            @NotBlank(message = "Nama folder wajib diisi.") @Size(max = 160) String folderName,
            @Size(max = 4000) String description) {
    }

    private RepositoryDtos() {
    }
}
