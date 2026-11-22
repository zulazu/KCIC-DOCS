package com.kcic.connect.distribution.infrastructure;

import com.kcic.connect.distribution.domain.DistributionEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DistributionEventRepository extends JpaRepository<DistributionEvent, UUID> {

    List<DistributionEvent> findByDistributionIdOrderByOccurredAtAsc(UUID distributionId);
}
