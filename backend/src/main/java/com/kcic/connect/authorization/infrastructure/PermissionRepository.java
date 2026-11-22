package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.domain.Permission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByPermissionCode(String permissionCode);

    List<Permission> findAllByOrderByModuleCodeAscPermissionCodeAsc();
}
