package com.hbsisms.feature.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Task 59 — bảng riêng cập giá bán theo giai đoạn (mỗi lần Áp dụng = một phiên bản).
 * {@code effectiveTo == null} = đang hiệu lực.
 */
@Entity
@Table(
        name = "sell_price_history",
        indexes = {
            @Index(name = "idx_sph_variant_from", columnList = "variant_id, effective_from"),
            @Index(name = "idx_sph_variant_open", columnList = "variant_id, effective_to")
        })
@Getter
@Setter
@NoArgsConstructor
public class SellPriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(name = "list_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal listPrice;

    @Column(name = "previous_list_price", precision = 14, scale = 2)
    private BigDecimal previousListPrice;

    @Column(name = "unit_cost", precision = 14, scale = 2)
    private BigDecimal unitCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_source", nullable = false, length = 20)
    private SellPriceCostSource costSource;

    @Column(name = "markup_percent", nullable = false)
    private int markupPercent;

    @Column(name = "markup_source", length = 40)
    private String markupSource;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    /** Null = phiên bản đang áp dụng. */
    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;
}
