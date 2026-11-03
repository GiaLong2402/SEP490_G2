package com.hbsisms.feature.approval;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long>, JpaSpecificationExecutor<ApprovalTask> {

    Optional<ApprovalTask> findByRefTypeAndRefIdAndStatus(String refType, Long refId, ApprovalStatus status);

    @EntityGraph(attributePaths = {"requestedBy", "decidedBy"})
    Optional<ApprovalTask> findWithActorsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"requestedBy", "decidedBy"})
    Page<ApprovalTask> findAll(Specification<ApprovalTask> spec, Pageable pageable);
}
