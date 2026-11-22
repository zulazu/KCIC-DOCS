package com.kcic.connect.distribution.infrastructure;

import com.kcic.connect.distribution.domain.Distribution;
import com.kcic.connect.distribution.domain.DistributionStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DistributionRepository
        extends JpaRepository<Distribution, UUID>, JpaSpecificationExecutor<Distribution> {

    boolean existsByDistributionCode(String distributionCode);

    List<Distribution> findAllByIdInAndSenderUserId(Collection<UUID> ids, UUID senderUserId);

    @Modifying
    @Query("update Distribution d set d.distributionStatus = :expired, d.updatedAt = :now "
            + "where d.distributionStatus in :active and d.expiresAt is not null and d.expiresAt <= :now")
    int expireDue(@Param("active") Collection<DistributionStatus> active,
                  @Param("expired") DistributionStatus expired, @Param("now") Instant now);
}
