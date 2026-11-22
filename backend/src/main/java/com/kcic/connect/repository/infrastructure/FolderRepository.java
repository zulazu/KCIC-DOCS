package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.Folder;
import com.kcic.connect.repository.domain.FolderStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FolderRepository extends JpaRepository<Folder, UUID> {

    List<Folder> findAllByWorkspaceIdOrderByFolderNameAsc(UUID workspaceId);

    List<Folder> findAllByWorkspaceIdAndFolderStatusOrderByFolderNameAsc(UUID workspaceId, FolderStatus status);

    List<Folder> findAllByWorkspaceIdIn(Collection<UUID> workspaceIds);

    @Query("""
            select (count(f) > 0) from Folder f
            where f.workspaceId = :workspaceId and f.folderStatus = :status
              and f.parentFolderId is null and lower(f.folderName) = lower(:folderName)
            """)
    boolean existsActiveRootFolderName(@Param("workspaceId") UUID workspaceId,
                                       @Param("folderName") String folderName,
                                       @Param("status") FolderStatus status);

    @Query("""
            select (count(f) > 0) from Folder f
            where f.workspaceId = :workspaceId and f.folderStatus = :status
              and f.parentFolderId = :parentFolderId and lower(f.folderName) = lower(:folderName)
            """)
    boolean existsActiveChildFolderName(@Param("workspaceId") UUID workspaceId,
                                        @Param("parentFolderId") UUID parentFolderId,
                                        @Param("folderName") String folderName,
                                        @Param("status") FolderStatus status);

    @Query("""
            select (count(f) > 0) from Folder f
            where f.workspaceId = :workspaceId and f.folderStatus = :status
              and f.parentFolderId is null and lower(f.folderName) = lower(:folderName)
              and f.id <> :excludeId
            """)
    boolean existsActiveRootFolderNameExcluding(@Param("workspaceId") UUID workspaceId,
                                                @Param("folderName") String folderName,
                                                @Param("status") FolderStatus status,
                                                @Param("excludeId") UUID excludeId);

    @Query("""
            select (count(f) > 0) from Folder f
            where f.workspaceId = :workspaceId and f.folderStatus = :status
              and f.parentFolderId = :parentFolderId and lower(f.folderName) = lower(:folderName)
              and f.id <> :excludeId
            """)
    boolean existsActiveChildFolderNameExcluding(@Param("workspaceId") UUID workspaceId,
                                                 @Param("parentFolderId") UUID parentFolderId,
                                                 @Param("folderName") String folderName,
                                                 @Param("status") FolderStatus status,
                                                 @Param("excludeId") UUID excludeId);

    boolean existsByParentFolderIdAndFolderStatus(UUID parentFolderId, FolderStatus status);

    long countByWorkspaceIdAndFolderStatus(UUID workspaceId, FolderStatus status);
}
