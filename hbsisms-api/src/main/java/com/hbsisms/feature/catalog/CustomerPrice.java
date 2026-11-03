package com.hbsisms.feature.catalog;

import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

/** B2B special price per tray for one customer and variant; overrides the list price once approved. */
@Entity
@Table(name = "customer_price", uniqueConstraints =
        @UniqueConstraint(name = "uk_custprice_customer_variant", columnNames = {"customer_id", "variant_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CustomerPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductVariant variant;

    /** Giá đang áp dụng (đã duyệt). Khi đang chờ duyệt, giá đề xuất nằm ở {@link #pendingPrice}. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Giá đề xuất đang chờ duyệt. Null khi không có đề xuất pending.
     * Tránh ghi đè {@link #unitPrice} trước khi Chủ duyệt (khớp FE mock).
     */
    @Column(precision = 14, scale = 2)
    private BigDecimal pendingPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    private LocalDate effectiveFrom;

    private LocalDate effectiveTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    private Instant approvedAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
