package com.kcic.connect.repository.api;

import com.kcic.connect.authorization.domain.AclEffect;
import com.kcic.connect.authorization.domain.PrincipalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ResourceAclDtos {

    public record GrantResponse(
            UUID id,
            String resourceType,
            UUID resourceId,
            String principalType,
            UUID principalId,
            String principalName,
            String permissionCode,
            String permissionName,
            String effect,
            String grantSource,
            boolean includeDescendants,
            UUID grantedBy,
            String grantedByName,
            Instant grantedAt,
            Instant expiresAt,
            boolean active,
            UUID revokedBy,
            String revokedByName,
            Instant revokedAt) {
    }

    public record CreateGrantRequest(
            @NotNull(message = "Tipe principal wajib dipilih.") PrincipalType principalType,
            @NotNull(message = "Principal wajib dipilih.") UUID principalId,
            @NotBlank(message = "Permission wajib dipilih.") String permissionCode,
            @NotNull(message = "Efek grant wajib dipilih.") AclEffect effect,
            Boolean includeDescendants,
            Instant expiresAt) {
    }

    public record AclSummaryResponse(
            String resourceType,
            UUID resourceId,
            boolean canManage,
            List<GrantResponse> active,
            List<GrantResponse> history) {
    }

    private ResourceAclDtos() {
    }
}
