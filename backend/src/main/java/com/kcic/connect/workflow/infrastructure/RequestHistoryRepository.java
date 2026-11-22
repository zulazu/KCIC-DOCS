package com.kcic.connect.workflow.infrastructure;

import com.kcic.connect.workflow.domain.RequestHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, UUID> {

    List<RequestHistory> findByRequestIdOrderByOccurredAtAsc(UUID requestId);
}
