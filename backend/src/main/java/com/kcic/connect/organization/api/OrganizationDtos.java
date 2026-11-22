package com.kcic.connect.organization.api;

import com.kcic.connect.organization.domain.UnitType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class OrganizationDtos {

    public record OrganizationUnitResponse(
            UUID id,
            UUID parentId,
            String unitCode,
            String unitName,
            String description,
            UnitType unitType,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record CreateOrganizationUnitRequest(
            UUID parentId,
            UnitType unitType,
            @NotBlank(message = "Kode unit wajib diisi.")
            @Pattern(regexp = "[A-Za-z0-9._-]{2,50}",
                    message = "Kode unit 2-50 karakter: huruf, angka, titik, garis bawah, atau strip.")
            String unitCode,
            @NotBlank(message = "Nama unit wajib diisi.") @Size(max = 160) String unitName,
            @Size(max = 4000) String description) {
    }

    public record UpdateOrganizationUnitRequest(
            UUID parentId,
            UnitType unitType,
            @NotBlank(message = "Nama unit wajib diisi.") @Size(max = 160) String unitName,
            @Size(max = 4000) String description,
            @NotBlank(message = "Status unit wajib diisi.") String status) {
    }

    public record UnitMemberResponse(
            UUID assignmentId,
            UUID userId,
            String username,
            String displayName,
            String assignmentType,
            boolean active,
            Instant validFrom,
            Instant validUntil) {
    }

    private OrganizationDtos() {
    }
}
