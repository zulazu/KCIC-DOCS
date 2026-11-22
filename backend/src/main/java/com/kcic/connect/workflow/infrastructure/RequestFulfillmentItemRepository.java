package com.kcic.connect.workflow.infrastructure;

import com.kcic.connect.workflow.domain.RequestFulfillmentItem;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestFulfillmentItemRepository extends JpaRepository<RequestFulfillmentItem, UUID> {

    List<RequestFulfillmentItem> findByFulfillmentIdInOrderByCreatedAtAsc(Collection<UUID> fulfillmentIds);
}
