package com.kcic.connect.organization.infrastructure;

import com.kcic.connect.organization.domain.AssignmentType;
import com.kcic.connect.organization.domain.UserOrganizationAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOrganizationAssignmentRepository extends JpaRepository<UserOrganizationAssignment, UUID> {

    List<UserOrganizationAssignment> findByUserIdAndIsActiveTrue(UUID userId);

    List<UserOrganizationAssignment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<UserOrganizationAssignment> findByIdAndUserId(UUID id, UUID userId);

    List<UserOrganizationAssignment> findByOrganizationUnitIdAndIsActiveTrue(UUID organizationUnitId);

    Optional<UserOrganizationAssignment> findFirstByUserIdAndAssignmentTypeAndIsActiveTrue(UUID userId,
                                                                                          AssignmentType type);
}
