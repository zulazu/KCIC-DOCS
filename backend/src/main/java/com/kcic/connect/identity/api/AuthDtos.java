package com.kcic.connect.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AuthDtos {

    public record LoginRequest(
            @NotBlank(message = "Username atau email wajib diisi.") String username,
            @NotBlank(message = "Password wajib diisi.") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Password saat ini wajib diisi.") String currentPassword,
            @NotBlank(message = "Password baru wajib diisi.") @Size(max = 200) String newPassword,
            @NotBlank(message = "Konfirmasi password wajib diisi.") String confirmPassword) {
    }

    public record CsrfResponse(String headerName, String parameterName, String token) {
    }

    public record UnitRef(UUID id, String unitCode, String unitName, String assignmentType) {
    }

    public record MeResponse(
            UUID userId,
            String username,
            String email,
            String displayName,
            boolean mustChangePassword,
            List<String> roles,
            List<String> capabilities,
            List<UnitRef> organizationUnits,
            Instant lastLoginAt) {
    }

    private AuthDtos() {
    }
}
