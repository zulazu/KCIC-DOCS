package com.kcic.connect.authorization.infrastructure;

import com.kcic.connect.authorization.domain.ResourcePermission;
import com.kcic.connect.authorization.domain.ResourceType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, UUID> {

    List<ResourcePermission> findByResourceTypeAndResourceIdAndRevokedAtIsNull(ResourceType type, UUID resourceId);

    List<ResourcePermission> findByResourceTypeAndResourceIdInAndRevokedAtIsNull(ResourceType type,
                                                                                Collection<UUID> resourceIds);

    List<ResourcePermission> findByResourceTypeAndResourceIdOrderByGrantedAtDesc(ResourceType type, UUID resourceId);
}
