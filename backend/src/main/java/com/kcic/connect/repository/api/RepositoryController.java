package com.kcic.connect.repository.api;

import com.kcic.connect.identity.application.UserPrincipal;
import com.kcic.connect.repository.api.RepositoryDtos.CreateFolderRequest;
import com.kcic.connect.repository.api.RepositoryDtos.CreateWorkspaceRequest;
import com.kcic.connect.repository.api.RepositoryDtos.FolderResponse;
import com.kcic.connect.repository.api.RepositoryDtos.UpdateFolderRequest;
import com.kcic.connect.repository.api.RepositoryDtos.UpdateWorkspaceRequest;
import com.kcic.connect.repository.api.RepositoryDtos.WorkspaceResponse;
import com.kcic.connect.repository.application.FolderService;
import com.kcic.connect.repository.application.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RepositoryController {

    private final WorkspaceService workspaceService;
    private final FolderService folderService;

    public RepositoryController(WorkspaceService workspaceService, FolderService folderService) {
        this.workspaceService = workspaceService;
        this.folderService = folderService;
    }

    // ------------------------------------------------------------ workspaces

    @GetMapping("/api/v1/workspaces")
    public List<WorkspaceResponse> listWorkspaces(@AuthenticationPrincipal UserPrincipal principal,
                                                  @RequestParam(defaultValue = "false") boolean includeArchived) {
        return workspaceService.list(UserPrincipal.require(principal).userId(), includeArchived);
    }

    @GetMapping("/api/v1/workspaces/{workspaceId}")
    public WorkspaceResponse getWorkspace(@AuthenticationPrincipal UserPrincipal principal,
                                          @PathVariable UUID workspaceId) {
        return workspaceService.get(UserPrincipal.require(principal).userId(), workspaceId);
    }

    @PostMapping("/api/v1/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse createWorkspace(@AuthenticationPrincipal UserPrincipal principal,
                                             @Valid @RequestBody CreateWorkspaceRequest request) {
        return workspaceService.create(UserPrincipal.require(principal).userId(), request);
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}")
    public WorkspaceResponse updateWorkspace(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID workspaceId,
                                             @Valid @RequestBody UpdateWorkspaceRequest request) {
        return workspaceService.update(UserPrincipal.require(principal).userId(), workspaceId, request);
    }

    // --------------------------------------------------------------- folders

    @GetMapping("/api/v1/workspaces/{workspaceId}/folders")
    public List<FolderResponse> listFolders(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable UUID workspaceId,
                                            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return folderService.list(UserPrincipal.require(principal).userId(), workspaceId, includeArchived);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/folders")
    @ResponseStatus(HttpStatus.CREATED)
    public FolderResponse createFolder(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID workspaceId,
                                       @Valid @RequestBody CreateFolderRequest request) {
        return folderService.create(UserPrincipal.require(principal).userId(), workspaceId, request);
    }

    @PutMapping("/api/v1/workspaces/{workspaceId}/folders/{folderId}")
    public FolderResponse updateFolder(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID workspaceId,
                                       @PathVariable UUID folderId,
                                       @Valid @RequestBody UpdateFolderRequest request) {
        return folderService.update(UserPrincipal.require(principal).userId(), workspaceId, folderId, request);
    }

    @PostMapping("/api/v1/workspaces/{workspaceId}/folders/{folderId}/archive")
    public FolderResponse archiveFolder(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID workspaceId,
                                        @PathVariable UUID folderId) {
        return folderService.archive(UserPrincipal.require(principal).userId(), workspaceId, folderId);
    }
}
