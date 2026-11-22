package com.kcic.connect.authorization.api;

import com.kcic.connect.authorization.domain.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RoleDtos {

    public record RoleResponse(
            UUID id,
            String roleCode,
            String roleName,
            String description,
            boolean systemRole,
            String status,
            List<String> permissions) {
    }

    public record PermissionResponse(
            UUID id,
            String permissionCode,
            String permissionName,
            String description,
            String moduleCode) {
    }

    public record CreateRoleRequest(
            @NotBlank(message = "Kode role wajib diisi.")
            @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{2,79}",
                    message = "Kode role 3-80 karakter: huruf, angka, atau garis bawah.")
            String roleCode,
            @NotBlank(message = "Nama role wajib diisi.") @Size(max = 120) String roleName,
            @Size(max = 4000) String description) {
    }

    public record UpdateRoleRequest(
            @NotBlank(message = "Nama role wajib diisi.") @Size(max = 120) String roleName,
            @Size(max = 4000) String description,
            @NotBlank(message = "Status role wajib diisi.") String status) {
    }

    public record SetRolePermissionsRequest(
            @NotNull(message = "Daftar permission wajib diisi.") Set<UUID> permissionIds) {
    }

    public record AssignRoleRequest(
            @NotBlank(message = "Role wajib diisi.") String roleCode,
            @NotNull(message = "Scope type wajib diisi.") ScopeType scopeType,
            UUID organizationUnitId,
            UUID workspaceId,
            Boolean includeDescendants,
            Instant validUntil) {
    }

    private RoleDtos() {
    }
}
