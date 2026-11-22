package com.kcic.connect.employee.infrastructure;

import com.kcic.connect.employee.domain.EmployeePlacement;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeePlacementRepository extends JpaRepository<EmployeePlacement, UUID> {

    List<EmployeePlacement> findByEmployeeIdOrderByValidFromDesc(UUID employeeId);

    Optional<EmployeePlacement> findFirstByEmployeeIdAndIsActiveTrue(UUID employeeId);

    List<EmployeePlacement> findByEmployeeIdInAndIsActiveTrue(Collection<UUID> employeeIds);

    List<EmployeePlacement> findByDivisionUnitIdAndIsActiveTrue(UUID divisionUnitId);

    boolean existsByEmployeeIdAndIsActiveTrue(UUID employeeId);
}
