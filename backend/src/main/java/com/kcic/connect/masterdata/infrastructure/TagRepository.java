package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByOrderByTagNameAsc();

    List<Tag> findAllByActiveTrueOrderByTagNameAsc();

    boolean existsByTagCodeIgnoreCase(String tagCode);

    boolean existsByTagCodeIgnoreCaseAndIdNot(String tagCode, UUID id);
}
