package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.ExternalResource;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalResourceRepository extends JpaRepository<ExternalResource, UUID> {
}
