package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleCode(String roleCode);

    List<Role> findAllByOrderByRoleCodeAsc();
}
