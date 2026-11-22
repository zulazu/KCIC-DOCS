package com.kcic.connect.distribution.infrastructure;

import com.kcic.connect.distribution.domain.DistributionAccessGrant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionAccessGrantRepository
        extends JpaRepository<DistributionAccessGrant, UUID> {

    List<DistributionAccessGrant> findByDistributionRecipientId(UUID distributionRecipientId);

    List<DistributionAccessGrant> findByDistributionRecipientIdIn(Collection<UUID> distributionRecipientIds);
}
