package com.hbsisms.feature.production;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialIssueRepository extends JpaRepository<MaterialIssue, Long> {

    @EntityGraph(attributePaths = {"productionOrder", "productionOrder.product", "createdBy", "completedBy"})
    @Query("select i from MaterialIssue i where i.id = :id")
    Optional<MaterialIssue> findWithHeaderById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MaterialIssue i where i.id = :id")
    Optional<MaterialIssue> findByIdForUpdate(@Param("id") Long id);

    Optional<MaterialIssue> findByProductionOrder_Id(Long productionOrderId);
}
