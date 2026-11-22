package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.Workspace;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findAllByOrderByWorkspaceNameAsc();

    List<Workspace> findAllByOwnerOrganizationUnitIdOrderByWorkspaceNameAsc(UUID unitId);

    boolean existsByWorkspaceCodeIgnoreCase(String workspaceCode);

    boolean existsByWorkspaceCodeIgnoreCaseAndIdNot(String workspaceCode, UUID id);
}
