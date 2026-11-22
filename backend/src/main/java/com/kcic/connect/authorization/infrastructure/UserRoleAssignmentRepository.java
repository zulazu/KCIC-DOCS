package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.domain.AssignmentStatus;
import com.kcic.connect.authorization.domain.UserRoleAssignment;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    List<UserRoleAssignment> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<UserRoleAssignment> findByIdAndUserId(UUID id, UUID userId);

    @Query("""
            select a from UserRoleAssignment a
            where a.userId = :userId
              and a.assignmentStatus = :status
              and a.validFrom <= :now
              and (a.validUntil is null or a.validUntil > :now)
            """)
    List<UserRoleAssignment> findEffectiveByUserId(@Param("userId") UUID userId,
                                                   @Param("status") AssignmentStatus status,
                                                   @Param("now") Instant now);

    @Query("""
            select distinct a.roleId from UserRoleAssignment a
            where a.userId = :userId
              and a.assignmentStatus = :status
              and a.validFrom <= :now
              and (a.validUntil is null or a.validUntil > :now)
            """)
    List<UUID> findEffectiveRoleIds(@Param("userId") UUID userId,
                                    @Param("status") AssignmentStatus status,
                                    @Param("now") Instant now);

    @Query("""
            select distinct a.workspaceId from UserRoleAssignment a
            where a.userId = :userId and a.workspaceId is not null
              and a.assignmentStatus = :status
              and a.validFrom <= :now
              and (a.validUntil is null or a.validUntil > :now)
            """)
    List<UUID> findEffectiveWorkspaceIds(@Param("userId") UUID userId,
                                         @Param("status") AssignmentStatus status,
                                         @Param("now") Instant now);

    @Query("""
            select (count(a) > 0) from UserRoleAssignment a
            where a.userId = :userId and a.roleId = :roleId
              and a.scopeType = :scope
              and a.assignmentStatus = :status
              and ((:unitId is null and a.organizationUnitId is null) or a.organizationUnitId = :unitId)
              and ((:workspaceId is null and a.workspaceId is null) or a.workspaceId = :workspaceId)
            """)
    boolean existsEffectiveAssignment(@Param("userId") UUID userId,
                                      @Param("roleId") UUID roleId,
                                      @Param("scope") com.kcic.connect.authorization.domain.ScopeType scope,
                                      @Param("unitId") UUID unitId,
                                      @Param("workspaceId") UUID workspaceId,
                                      @Param("status") AssignmentStatus status);

    @Query("""
            select distinct a.userId from UserRoleAssignment a
            where a.roleId = :roleId
              and a.scopeType = :scope
              and ((:unitId is null and a.organizationUnitId is null) or a.organizationUnitId = :unitId)
              and a.assignmentStatus = :status
              and a.validFrom <= :now
              and (a.validUntil is null or a.validUntil > :now)
            """)
    List<UUID> findEffectiveUserIdsByRoleAndScope(@Param("roleId") UUID roleId,
                                                  @Param("scope") com.kcic.connect.authorization.domain.ScopeType scope,
                                                  @Param("unitId") UUID unitId,
                                                  @Param("status") AssignmentStatus status,
                                                  @Param("now") Instant now);
}
