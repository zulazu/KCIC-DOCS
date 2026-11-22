package com.kcic.connect.repository.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.repository.api.ResourceAclDtos.AclSummaryResponse;
import com.kcic.connect.repository.api.ResourceAclDtos.CreateGrantRequest;
import com.kcic.connect.repository.api.ResourceAclDtos.GrantResponse;
import com.kcic.connect.repository.application.ResourceAclService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access-management endpoints. Every route resolves the resource first and checks
 * the managing capability inside ResourceAclService; hiding the UI is never the
 * control.
 */
@RestController
public class ResourceAclController {

    private final ResourceAclService aclService;

    public ResourceAclController(ResourceAclService aclService) {
        this.aclService = aclService;
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/access")
    public AclSummaryResponse workspaceAccess(@AuthenticationPrincipal UserPrincipal principal,
                                              @PathVariable UUID workspaceId,
                                              @RequestParam(defaultValue = "false") boolean includeHistory) {
        return aclService.workspace(UserPrincipal.require(principal).userId(), workspaceId, includeHistory);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse grantWorkspace(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID workspaceId,
                                        @Valid @RequestBody CreateGrantRequest request) {
        return aclService.grantWorkspace(UserPrincipal.require(principal).userId(), workspaceId, request);
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/access/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeWorkspace(@AuthenticationPrincipal UserPrincipal principal,
                                @PathVariable UUID workspaceId,
                                @PathVariable UUID grantId) {
        aclService.revokeWorkspace(UserPrincipal.require(principal).userId(), workspaceId, grantId);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}/folders/{folderId}/access")
    public AclSummaryResponse folderAccess(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable UUID workspaceId,
                                           @PathVariable UUID folderId,
                                           @RequestParam(defaultValue = "false") boolean includeHistory) {
        return aclService.folder(UserPrincipal.require(principal).userId(), workspaceId, folderId, includeHistory);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/folders/{folderId}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse grantFolder(@AuthenticationPrincipal UserPrincipal principal,
                                     @PathVariable UUID workspaceId,
                                     @PathVariable UUID folderId,
                                     @Valid @RequestBody CreateGrantRequest request) {
        return aclService.grantFolder(UserPrincipal.require(principal).userId(), workspaceId, folderId, request);
    }

    @DeleteMapping("/api/v1/workspaces/{workspaceId}/folders/{folderId}/access/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeFolder(@AuthenticationPrincipal UserPrincipal principal,
                             @PathVariable UUID workspaceId,
                             @PathVariable UUID folderId,
                             @PathVariable UUID grantId) {
        aclService.revokeFolder(UserPrincipal.require(principal).userId(), workspaceId, folderId, grantId);
    }

    @GetMapping("/api/v1/documents/{documentId}/access")
    public AclSummaryResponse documentAccess(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID documentId,
                                             @RequestParam(defaultValue = "false") boolean includeHistory) {
        return aclService.document(UserPrincipal.require(principal).userId(), documentId, includeHistory);
    }

    @PostMapping("/api/v1/documents/{documentId}/access")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse grantDocument(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID documentId,
                                       @Valid @RequestBody CreateGrantRequest request) {
        return aclService.grantDocument(UserPrincipal.require(principal).userId(), documentId, request);
    }

    @DeleteMapping("/api/v1/documents/{documentId}/access/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeDocument(@AuthenticationPrincipal UserPrincipal principal,
                               @PathVariable UUID documentId,
                               @PathVariable UUID grantId) {
        aclService.revokeDocument(UserPrincipal.require(principal).userId(), documentId, grantId);
    }
}
