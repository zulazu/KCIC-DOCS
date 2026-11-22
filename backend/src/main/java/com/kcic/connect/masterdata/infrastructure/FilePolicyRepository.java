package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.FilePolicy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilePolicyRepository extends JpaRepository<FilePolicy, UUID> {

    List<FilePolicy> findAllByOrderByPolicyNameAsc();

    List<FilePolicy> findAllByActiveTrueOrderByPolicyNameAsc();

    Optional<FilePolicy> findFirstByDefaultPolicyTrueAndActiveTrue();
}
