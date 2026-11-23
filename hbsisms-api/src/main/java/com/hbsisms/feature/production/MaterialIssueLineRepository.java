package com.hbsisms.feature.production;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialIssueLineRepository extends JpaRepository<MaterialIssueLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "rawMaterialLot"})
    List<MaterialIssueLine> findByMaterialIssue_IdOrderBySortOrderAscIdAsc(Long materialIssueId);
}
