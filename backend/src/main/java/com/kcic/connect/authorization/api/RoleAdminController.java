package com.kcic.connect.authorization.api;

import com.kcic.connect.authorization.api.RoleDtos.AssignRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.CreateRoleRequest;
import com.kcic.connect.authorization.api.RoleDtos.PermissionResponse;
import com.kcic.connect.authorization.api.RoleDtos.RoleHolderView;
import com.kcic.connect.authorization.api.RoleDtos.RoleResponse;
import com.kcic.connect.authorization.api.RoleDtos.SetRolePermissionsRequest;
import com.kcic.connect.authorization.api.RoleDtos.UpdateRoleRequest;
import com.kcic.connect.authorization.application.RoleAdminService;
import com.kcic.connect.identity.api.UserDtos.RoleAssignmentView;
import com.kcic.connect.identity.application.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class RoleAdminController {

    private final RoleAdminService roleAdminService;

    public RoleAdminController(RoleAdminService roleAdminService) {
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/roles")
    public List<RoleResponse> roles(@AuthenticationPrincipal UserPrincipal principal) {
        return roleAdminService.roles(UserPrincipal.require(principal).userId());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@AuthenticationPrincipal UserPrincipal principal,
                                   @Valid @RequestBody CreateRoleRequest request) {
        return roleAdminService.createRole(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/roles/{roleId}")
    public RoleResponse updateRole(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID roleId,
                                   @Valid @RequestBody UpdateRoleRequest request) {
        return roleAdminService.updateRole(UserPrincipal.require(principal).userId(), roleId, request);
    }

    @PutMapping("/roles/{roleId}/permissions")
    public RoleResponse setPermissions(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID roleId,
                                       @Valid @RequestBody SetRolePermissionsRequest request) {
        return roleAdminService.setPermissions(UserPrincipal.require(principal).userId(), roleId,
                request.permissionIds());
    }

    @GetMapping("/roles/{roleId}/assignments")
    public List<RoleHolderView> roleAssignments(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID roleId) {
        return roleAdminService.roleAssignments(UserPrincipal.require(principal).userId(), roleId);
    }

    @GetMapping("/permissions")
    public List<PermissionResponse> permissions(@AuthenticationPrincipal UserPrincipal principal) {
        return roleAdminService.permissions(UserPrincipal.require(principal).userId());
    }

    @GetMapping("/users/{userId}/role-assignments")
    public List<RoleAssignmentView> assignments(@AuthenticationPrincipal UserPrincipal principal,
                                                @PathVariable UUID userId) {
        return roleAdminService.assignments(UserPrincipal.require(principal).userId(), userId);
    }

    @PostMapping("/users/{userId}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentView assign(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID userId,
                                     @Valid @RequestBody AssignRoleRequest request) {
        return roleAdminService.assign(UserPrincipal.require(principal).userId(), userId, request);
    }

    @DeleteMapping("/role-assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal UserPrincipal principal,
                       @PathVariable UUID assignmentId) {
        roleAdminService.revoke(UserPrincipal.require(principal).userId(), assignmentId);
    }
}
