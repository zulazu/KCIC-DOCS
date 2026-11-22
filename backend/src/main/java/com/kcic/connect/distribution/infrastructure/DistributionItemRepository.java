package com.kcic.connect.distribution.infrastructure;

import com.kcic.connect.distribution.domain.DistributionItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionItemRepository extends JpaRepository<DistributionItem, UUID> {

    List<DistributionItem> findByDistributionIdOrderByCreatedAtAsc(UUID distributionId);

    List<DistributionItem> findByDistributionIdIn(Collection<UUID> distributionIds);
}
