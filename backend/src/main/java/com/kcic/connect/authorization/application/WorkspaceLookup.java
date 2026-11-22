package com.kcic.connect.authorization.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the repository module so authorization can validate
 * workspace-scoped role assignments without owning workspace data.
 */
public interface WorkspaceLookup {

    boolean workspaceExists(UUID workspaceId);

    Optional<UUID> workspaceOwnerUnit(UUID workspaceId);
}
