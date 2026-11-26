package com.hbsisms.feature.system;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Ai sửa đơn lúc nào — Inter 3 dòng 6. */
    @EntityGraph(attributePaths = "user")
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @EntityGraph(attributePaths = "user")
    Page<AuditLog> findAll(Specification<AuditLog> spec, Pageable pageable);
}
