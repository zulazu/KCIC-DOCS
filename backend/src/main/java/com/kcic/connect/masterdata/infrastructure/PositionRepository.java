package com.kcic.connect.masterdata.infrastructure;

import com.kcic.connect.masterdata.domain.Position;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PositionRepository extends JpaRepository<Position, UUID> {

    List<Position> findAllByOrderByPositionNameAsc();

    List<Position> findAllByActiveTrueOrderByPositionNameAsc();

    boolean existsByPositionCodeIgnoreCase(String positionCode);

    @Query("select p from Position p where lower(p.positionCode) in :codes")
    List<Position> findByPositionCodeLowerIn(@Param("codes") Collection<String> lowerCaseCodes);
}