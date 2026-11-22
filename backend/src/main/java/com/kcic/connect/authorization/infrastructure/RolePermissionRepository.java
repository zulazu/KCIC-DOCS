package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.domain.RolePermission;
import com.kcic.connect.authorization.domain.RolePermissionId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    List<RolePermission> findByRoleId(UUID roleId);

    @Modifying
    @Query("delete from RolePermission rp where rp.roleId = :roleId")
    void deleteByRoleId(UUID roleId);

    @Query("""
            select rp.roleId, p.permissionCode
            from RolePermission rp
            join Permission p on p.id = rp.permissionId
            where rp.roleId in :roleIds
            """)
    List<Object[]> findPermissionCodesByRoleIds(List<UUID> roleIds);
}
