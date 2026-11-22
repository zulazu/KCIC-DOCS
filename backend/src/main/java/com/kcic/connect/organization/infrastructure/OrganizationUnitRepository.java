package com.kcic.connect.organization.infrastructure;

import com.kcic.connect.organization.domain.OrganizationUnit;
import com.kcic.connect.organization.domain.UnitStatus;
import com.kcic.connect.organization.domain.UnitType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, UUID> {

    Optional<OrganizationUnit> findByUnitCodeIgnoreCase(String unitCode);

    @Query("select (count(u) > 0) from OrganizationUnit u where lower(u.unitCode) = lower(:code)")
    boolean existsByUnitCodeIgnoreCase(String code);

    List<OrganizationUnit> findAllByOrderByUnitNameAsc();

    List<OrganizationUnit> findAllByUnitTypeOrderByUnitNameAsc(UnitType unitType);

    List<OrganizationUnit> findAllByUnitTypeAndUnitStatus(UnitType unitType, UnitStatus unitStatus);

    List<OrganizationUnit> findByParentId(UUID parentId);

    long countByParentId(UUID parentId);

    @Query(value = """
            WITH RECURSIVE ancestry AS (
                SELECT id, parent_id
                FROM organization.organization_units
                WHERE id IN (:unitIds)
                UNION
                SELECT u.id, u.parent_id
                FROM organization.organization_units u
                JOIN ancestry a ON u.id = a.parent_id
            )
            SELECT distinct id FROM ancestry
            """, nativeQuery = true)
    List<UUID> findAncestorIds(@Param("unitIds") Collection<UUID> unitIds);
}
