package com.kcic.connect.repository.infrastructure;

import com.kcic.connect.repository.domain.StorageObject;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageObjectRepository extends JpaRepository<StorageObject, UUID> {
}
