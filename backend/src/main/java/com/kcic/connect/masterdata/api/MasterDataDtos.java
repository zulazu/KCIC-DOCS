package com.kcic.connect.masterdata.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MasterDataDtos {

    public record CategoryResponse(UUID id, UUID parentId, String categoryCode, String categoryName,
                                   String description, boolean active, Instant createdAt, Instant updatedAt) {
    }

    public record CreateCategoryRequest(
            UUID parentId,
            @NotBlank(message = "Kode kategori wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,60}",
                    message = "Kode kategori 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String categoryCode,
            @NotBlank(message = "Nama kategori wajib diisi.") @Size(max = 140) String categoryName,
            @Size(max = 4000) String description) {
    }

    public record UpdateCategoryRequest(
            UUID parentId,
            @NotBlank(message = "Nama kategori wajib diisi.") @Size(max = 140) String categoryName,
            @Size(max = 4000) String description,
            @NotNull(message = "Status aktif wajib diisi.") Boolean active) {
    }

    public record TypeResponse(UUID id, String typeCode, String typeName, String description, boolean active,
                               Instant createdAt, Instant updatedAt) {
    }

    public record CreateTypeRequest(
            @NotBlank(message = "Kode tipe wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,60}",
                    message = "Kode tipe 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String typeCode,
            @NotBlank(message = "Nama tipe wajib diisi.") @Size(max = 140) String typeName,
            @Size(max = 4000) String description) {
    }

    public record UpdateTypeRequest(
            @NotBlank(message = "Nama tipe wajib diisi.") @Size(max = 140) String typeName,
            @Size(max = 4000) String description,
            @NotNull(message = "Status aktif wajib diisi.") Boolean active) {
    }

    public record ClassificationResponse(UUID id, String classificationCode, String classificationName,
                                         short sensitivityRank, String description, boolean active,
                                         Instant createdAt, Instant updatedAt) {
    }

    public record CreateClassificationRequest(
            @NotBlank(message = "Kode klasifikasi wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,40}",
                    message = "Kode klasifikasi 2-40 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String classificationCode,
            @NotBlank(message = "Nama klasifikasi wajib diisi.") @Size(max = 100) String classificationName,
            @Min(value = 1, message = "Peringkat sensitivitas minimal 1.")
            @Max(value = 100, message = "Peringkat sensitivitas maksimal 100.")
            short sensitivityRank,
            @Size(max = 4000) String description) {
    }

    public record UpdateClassificationRequest(
            @NotBlank(message = "Nama klasifikasi wajib diisi.") @Size(max = 100) String classificationName,
            @Min(value = 1, message = "Peringkat sensitivitas minimal 1.")
            @Max(value = 100, message = "Peringkat sensitivitas maksimal 100.")
            short sensitivityRank,
            @Size(max = 4000) String description,
            @NotNull(message = "Status aktif wajib diisi.") Boolean active) {
    }

    public record TagResponse(UUID id, String tagCode, String tagName, boolean active, Instant createdAt,
                              Instant updatedAt) {
    }

    public record CreateTagRequest(
            @NotBlank(message = "Kode tag wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,60}",
                    message = "Kode tag 2-60 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String tagCode,
            @NotBlank(message = "Nama tag wajib diisi.") @Size(max = 100) String tagName) {
    }

    public record UpdateTagRequest(
            @NotBlank(message = "Nama tag wajib diisi.") @Size(max = 100) String tagName,
            @NotNull(message = "Status aktif wajib diisi.") Boolean active) {
    }

    public record FilePolicyResponse(UUID id, String policyCode, String policyName, List<String> allowedExtensions,
                                     List<String> allowedMimeTypes, long maxFileSizeBytes, boolean defaultPolicy,
                                     boolean active, Instant updatedAt) {
    }

    public record UpdateFilePolicyRequest(
            @NotEmpty(message = "Minimal satu ekstensi diizinkan.")
            @Size(max = 50, message = "Maksimal 50 ekstensi.")
            List<@Pattern(regexp = "[A-Za-z0-9]{1,12}",
                    message = "Ekstensi hanya huruf/angka, tanpa titik.") String> allowedExtensions,
            @NotEmpty(message = "Minimal satu MIME type diizinkan.")
            @Size(max = 50, message = "Maksimal 50 MIME type.")
            List<@Pattern(regexp = "[a-z0-9.+-]+/[a-z0-9.+-]+",
                    message = "Format MIME type tidak valid, contoh application/pdf.") String> allowedMimeTypes,
            @Min(value = 1, message = "Ukuran maksimum harus lebih dari 0 byte.") long maxFileSizeBytes) {
    }

    private MasterDataDtos() {
    }
}
