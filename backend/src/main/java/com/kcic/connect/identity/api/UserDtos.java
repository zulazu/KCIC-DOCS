package com.kcic.connect.identity.api;

import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.organization.domain.AssignmentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UserDtos {

    public record UserSummaryResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            AccountStatus status,
            boolean mustChangePassword,
            UUID employeeId,
            Instant lastLoginAt,
            Instant createdAt) {
    }

    public record RoleAssignmentView(
            UUID id,
            UUID roleId,
            String roleCode,
            String roleName,
            String scopeType,
            UUID organizationUnitId,
            UUID workspaceId,
            boolean includeDescendants,
            Instant validFrom,
            Instant validUntil,
            String status,
            Instant createdAt) {
    }

    public record UnitMembershipView(
            UUID id,
            UUID organizationUnitId,
            String unitCode,
            String unitName,
            AssignmentType assignmentType,
            boolean active,
            Instant validFrom,
            Instant validUntil) {
    }

    public record UserDetailResponse(
            UserSummaryResponse user,
            List<RoleAssignmentView> roleAssignments,
            List<UnitMembershipView> organizationUnits) {
    }

    public record CreateUserRequest(
            @NotBlank(message = "Username wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,100}",
                    message = "Username 3-100 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String username,
            @Email(message = "Format email tidak valid.") @Size(max = 254) String email,
            @NotBlank(message = "Nama tampilan wajib diisi.") @Size(max = 160) String displayName,
            @NotBlank(message = "Password sementara wajib diisi.") @Size(max = 200) String temporaryPassword) {
    }

    public record UpdateUserRequest(
            @NotBlank(message = "Nama tampilan wajib diisi.") @Size(max = 160) String displayName,
            @Email(message = "Format email tidak valid.") @Size(max = 254) String email) {
    }

    public record ChangeStatusRequest(
            @NotNull(message = "Status wajib diisi.") AccountStatus status) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Password sementara wajib diisi.") @Size(max = 200) String temporaryPassword) {
    }

    public record AssignUnitRequest(
            @NotNull(message = "Unit wajib diisi.") UUID organizationUnitId,
            @NotNull(message = "Tipe assignment wajib diisi.") AssignmentType assignmentType,
            Instant validUntil) {
    }

    private UserDtos() {
    }
}
