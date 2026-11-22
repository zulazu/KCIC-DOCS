package com.kcic.connect.repository.application;

import com.kcic.connect.authorization.application.AuthorizationService;
import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.infrastructure.FolderRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Resolves the folder grants that apply to a folder including every ancestor in its
 * real parent chain. Folder ACLs are subtree grants, so document and folder visibility
 * must evaluate the whole ancestry rather than only the nearest folder.
 */
@Component
public class FolderGrantResolver {

    private final FolderRepository folderRepository;
    private final AuthorizationService authorizationService;

    public FolderGrantResolver(FolderRepository folderRepository, AuthorizationService authorizationService) {
        this.folderRepository = folderRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * Resolves ancestry grants for many folders in one pass so list endpoints do not
     * issue a query per row.
     */
    public Map<UUID, List<ResourcePermission>> grantsFor(Collection<UUID> folderIds) {
        Set<UUID> roots = folderIds.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (roots.isEmpty()) {
            // HashMap tolerates a null folderId lookup; Map.of() throws on get(null).
            return new HashMap<>();
        }
        Map<UUID, Folder> folders = new HashMap<>();
        Set<UUID> frontier = new HashSet<>(roots);
        while (!frontier.isEmpty()) {
            List<Folder> found = folderRepository.findAllById(frontier);
            frontier = new HashSet<>();
            for (Folder folder : found) {
                folders.put(folder.getId(), folder);
                UUID parent = folder.getParentFolderId();
                if (parent != null && !folders.containsKey(parent)) {
                    frontier.add(parent);
                }
            }
        }
        Map<UUID, List<ResourcePermission>> grantsByFolder = folders.isEmpty()
                ? Map.of()
                : authorizationService.activeResourcePermissions(ResourceType.FOLDER, folders.keySet());
        Map<UUID, List<ResourcePermission>> result = new HashMap<>();
        for (UUID folderId : roots) {
            result.put(folderId, ancestryGrants(grantsByFolder, folders, folderId));
        }
        return result;
    }

    /** Returns ancestry grants for one folder; a document at workspace root has none. */
    public List<ResourcePermission> grantsFor(UUID folderId) {
        if (folderId == null) {
            return List.of();
        }
        return grantsFor(List.of(folderId)).getOrDefault(folderId, List.of());
    }

    private static List<ResourcePermission> ancestryGrants(Map<UUID, List<ResourcePermission>> grantsByFolder,
                                                           Map<UUID, Folder> folders, UUID folderId) {
        List<ResourcePermission> grants = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        UUID cursor = folderId;
        while (cursor != null && seen.add(cursor)) {
            grants.addAll(grantsByFolder.getOrDefault(cursor, List.of()));
            Folder folder = folders.get(cursor);
            cursor = folder == null ? null : folder.getParentFolderId();
        }
        return grants;
    }
}
