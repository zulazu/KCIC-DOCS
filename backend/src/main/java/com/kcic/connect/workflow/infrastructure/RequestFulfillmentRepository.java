package com.kcic.connect.workflow.infrastructure;

import com.kcic.connect.workflow.domain.RequestFulfillment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestFulfillmentRepository extends JpaRepository<RequestFulfillment, UUID> {

    List<RequestFulfillment> findByRequestIdOrderByFulfilledAtAsc(UUID requestId);
}
