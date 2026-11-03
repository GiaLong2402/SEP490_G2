package com.hbsisms.feature.approval;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {

    Optional<ApprovalTask> findByRefTypeAndRefIdAndStatus(String refType, Long refId, ApprovalStatus status);
}
