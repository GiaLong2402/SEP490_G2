package com.hbsisms.feature.payment;

import com.hbsisms.feature.sales.SalesOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Task 71 — một lần thu ({@link Payment}) có thể trừ vào nhiều đơn.
 * Mỗi dòng: số tiền của phiếu thu gắn với một {@link SalesOrder}.
 */
@Entity
@Table(
        name = "payment_allocation",
        indexes = {
            @Index(name = "idx_alloc_payment", columnList = "payment_id"),
            @Index(name = "idx_alloc_order", columnList = "order_id")
        },
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_alloc_payment_order",
                        columnNames = {"payment_id", "order_id"}))
@Check(name = "chk_alloc_amount_positive", constraints = "amount > 0")
@Getter
@Setter
@NoArgsConstructor
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SalesOrder order;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
