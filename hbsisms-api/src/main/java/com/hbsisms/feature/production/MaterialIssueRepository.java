package com.hbsisms.feature.production;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, Long> {

    @EntityGraph(attributePaths = {"productionOrder", "productionOrder.product", "createdBy", "completedBy"})
    @Query("select i from MaterialIssue i where i.id = :id")
    Optional<MaterialIssue> findWithHeaderById(@Param("id") Long id);

    Optional<MaterialIssue> findByProductionOrder_Id(Long productionOrderId);
}
