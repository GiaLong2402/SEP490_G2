package com.hbsisms.feature.costing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kỳ giá vốn theo tháng (Inter 1 task 55).
 * {@code periodStart} luôn là ngày 1 của tháng; unique → mỗi tháng tối đa 1 kỳ.
 */
@Entity
@Table(
        name = "cost_period",
        indexes = @Index(name = "idx_cost_period_status", columnList = "status"),
        uniqueConstraints = @UniqueConstraint(name = "uk_cost_period_start", columnNames = "period_start"))
@Getter
@Setter
@NoArgsConstructor
public class CostPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    /** Ngày đầu tháng của kỳ (vd. 2026-11-01). */
    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CostPeriodStatus status = CostPeriodStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "opened_by_user_id")
    private Long openedByUserId;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    /** Lý do mở lại kỳ đã khóa (task 58); null khi chưa từng reopen. */
    @Column(name = "reopen_reason", length = 500)
    private String reopenReason;
}
