package com.kcic.connect.workflow.infrastructure;

import com.kcic.connect.workflow.domain.ApprovalDecision;
import com.kcic.connect.workflow.domain.RequestApproval;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestApprovalRepository extends JpaRepository<RequestApproval, UUID> {

    List<RequestApproval> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    Optional<RequestApproval> findFirstByRequestIdAndDecisionOrderByCreatedAtAsc(UUID requestId,
                                                                               ApprovalDecision decision);
}
