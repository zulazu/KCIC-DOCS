package com.kcic.connect.employee.api;

import com.kcic.connect.employee.domain.EmploymentStatus;
import com.kcic.connect.identity.domain.AccountStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class EmployeeDtos {

    public record EmployeeSummaryResponse(
            UUID id,
            String nip,
            String fullName,
            String email,
            EmploymentStatus employmentStatus,
            UUID activePlacementId,
            UUID divisionUnitId,
            String divisionCode,
            String divisionName,
            UUID departmentId,
            String departmentName,
            UUID directorateId,
            String directorateName,
            boolean hasAccount,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PlacementView(
            UUID id,
            UUID divisionUnitId,
            String divisionCode,
            String divisionName,
            String departmentName,
            String directorateName,
            Instant validFrom,
            Instant validUntil,
            boolean active,
            Instant createdAt) {
    }

    public record LinkedAccountView(
            UUID userId,
            String username,
            AccountStatus status,
            boolean mustChangePassword,
            Instant lastLoginAt) {
    }

    public record EmployeeDetailResponse(
            EmployeeSummaryResponse employee,
            List<PlacementView> placements,
            LinkedAccountView linkedAccount) {
    }

    public record CreateEmployeeRequest(
            @NotBlank(message = "NIP wajib diisi.")
            @Size(max = 30, message = "NIP maksimal 30 karakter.")
            String nip,
            @NotBlank(message = "Nama lengkap wajib diisi.")
            @Size(max = 160, message = "Nama lengkap maksimal 160 karakter.")
            String fullName,
            @NotBlank(message = "Email wajib diisi.")
            @Email(message = "Format email tidak valid.")
            @Size(max = 254, message = "Email maksimal 254 karakter.")
            String email,
            @NotNull(message = "Status kepegawaian wajib diisi.")
            EmploymentStatus employmentStatus,
            @NotNull(message = "Biro/Division wajib diisi.")
            UUID divisionUnitId) {
    }

    public record UpdateEmployeeRequest(
            @NotBlank(message = "NIP wajib diisi.")
            @Size(max = 30, message = "NIP maksimal 30 karakter.")
            String nip,
            @NotBlank(message = "Nama lengkap wajib diisi.")
            @Size(max = 160, message = "Nama lengkap maksimal 160 karakter.")
            String fullName,
            @NotBlank(message = "Email wajib diisi.")
            @Email(message = "Format email tidak valid.")
            @Size(max = 254, message = "Email maksimal 254 karakter.")
            String email,
            @NotNull(message = "Status kepegawaian wajib diisi.")
            EmploymentStatus employmentStatus) {
    }

    public record TransferPlacementRequest(
            @NotNull(message = "Biro/Division tujuan wajib diisi.")
            UUID divisionUnitId,
            UUID expectedPlacementId) {
    }

    public record CreateEmployeeAccountRequest(
            @NotBlank(message = "Username wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,100}",
                    message = "Username 3-100 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String username,
            @NotBlank(message = "Password sementara wajib diisi.")
            @Size(max = 200, message = "Password maksimal 200 karakter.")
            String temporaryPassword) {
    }

    public record LinkEmployeeAccountRequest(
            @NotNull(message = "Employee wajib dipilih.")
            UUID employeeId) {
    }

    private EmployeeDtos() {
    }
}
