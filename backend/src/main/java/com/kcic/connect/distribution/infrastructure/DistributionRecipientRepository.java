package com.kcic.connect.distribution.infrastructure;

import com.kcic.connect.distribution.domain.DistributionRecipient;
import com.kcic.connect.distribution.domain.RecipientStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DistributionRecipientRepository
        extends JpaRepository<DistributionRecipient, UUID>, JpaSpecificationExecutor<DistributionRecipient> {

    List<DistributionRecipient> findByDistributionIdOrderBySentAtAsc(UUID distributionId);

    List<DistributionRecipient> findByDistributionIdIn(Collection<UUID> distributionIds);

    Optional<DistributionRecipient> findByDistributionIdAndRecipientUserId(UUID distributionId,
                                                                           UUID recipientUserId);

    List<DistributionRecipient> findByDistributionIdInAndRecipientUserId(Collection<UUID> distributionIds,
                                                                         UUID recipientUserId);

    @Modifying
    @Query("update DistributionRecipient r set r.recipientStatus = :expired "
            + "where r.recipientStatus in :active and r.expiresAt is not null and r.expiresAt <= :now")
    int expireDue(@Param("active") Collection<RecipientStatus> active,
                  @Param("expired") RecipientStatus expired, @Param("now") Instant now);
}
