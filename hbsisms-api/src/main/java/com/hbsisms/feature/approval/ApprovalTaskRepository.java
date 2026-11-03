package com.hbsisms.feature.approval;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long>, JpaSpecificationExecutor<ApprovalTask> {

    Optional<ApprovalTask> findByRefTypeAndRefIdAndStatus(String refType, Long refId, ApprovalStatus status);

    @EntityGraph(attributePaths = {"requestedBy", "decidedBy"})
    Optional<ApprovalTask> findWithActorsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"requestedBy", "decidedBy"})
    Page<ApprovalTask> findAll(Specification<ApprovalTask> spec, Pageable pageable);

    /** Việc đã quyết định (duyệt/từ chối) — lấy ghi chú gần nhất cho staff xem trên tab Giá riêng. */
    @Query("""
            select t from ApprovalTask t
            where t.refType = :refType
              and t.refId in :refIds
              and t.status in :statuses
              and t.decidedAt is not null
            order by t.decidedAt desc
            """)
    List<ApprovalTask> findDecidedByRefTypeAndRefIdIn(
            @Param("refType") String refType,
            @Param("refIds") Collection<Long> refIds,
            @Param("statuses") Collection<ApprovalStatus> statuses);
}
