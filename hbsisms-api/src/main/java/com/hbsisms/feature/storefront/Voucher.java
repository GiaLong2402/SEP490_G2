package com.hbsisms.feature.storefront;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Discount code; redemptions are tracked via {@code sales_order.voucher_code} and {@code usedCount}. */
@Entity
@Table(name = "voucher")
@Getter
@Setter
@NoArgsConstructor
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherType voucherType;

    /** Percent (0–100) or VND amount, depending on {@link #voucherType}. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal minOrder = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal maxDiscount;

    private Instant startsAt;

    private Instant endsAt;

    private Integer usageLimit;

    private Integer perCustomerLimit;

    @Column(nullable = false)
    private int usedCount;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
