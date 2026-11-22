package com.kcic.connect.template.infrastructure;

import com.kcic.connect.template.domain.TemplateUsageEvent;
import com.kcic.connect.template.domain.TemplateUsageType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateUsageEventRepository extends JpaRepository<TemplateUsageEvent, UUID> {

    long countByTemplateId(UUID templateId);

    long countByTemplateIdAndUsageType(UUID templateId, TemplateUsageType usageType);
}
