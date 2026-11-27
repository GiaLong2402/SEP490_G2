package com.hbsisms.feature.costing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CostPeriodRepository extends JpaRepository<CostPeriod, Long> {

    Optional<CostPeriod> findByStatus(CostPeriodStatus status);

    Optional<CostPeriod> findByPeriodStart(LocalDate periodStart);

    boolean existsByStatus(CostPeriodStatus status);

    List<CostPeriod> findAllByOrderByPeriodStartDesc();

    Optional<CostPeriod> findFirstByStatusOrderByPeriodStartDesc(CostPeriodStatus status);

    /** Có kỳ tháng sau {@code periodStart} không (chặn reopen / bắt hủy kỳ mở nhầm trước). */
    boolean existsByPeriodStartAfter(LocalDate periodStart);

    Optional<CostPeriod> findFirstByOrderByPeriodStartDesc();
}
