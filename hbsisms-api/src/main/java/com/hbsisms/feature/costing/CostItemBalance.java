package com.hbsisms.feature.costing;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Số dư giá vốn theo mặt hàng trong một kỳ (Inter 1 task 55).
 * Tồn cuối / đơn giá bình quân được cập nhật khi ghi nhận nhập–xuất (56) và khóa sổ (57).
 */
@Entity
@Table(
        name = "cost_item_balance",
        indexes = {
            @Index(name = "idx_cost_bal_period", columnList = "period_id"),
            @Index(name = "idx_cost_bal_item", columnList = "item_type, item_id")
        },
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_cost_bal_period_item",
                        columnNames = {"period_id", "item_type", "item_id"}))
@Check(
        name = "chk_cost_bal_nonneg",
        constraints =
                "opening_qty >= 0 AND opening_value >= 0 AND inbound_qty >= 0 AND inbound_value >= 0"
                        + " AND outbound_qty >= 0 AND outbound_value >= 0"
                        + " AND closing_qty >= 0 AND closing_value >= 0")
@Getter
@Setter
@NoArgsConstructor
public class CostItemBalance {

    private static final int QTY_SCALE = 3;
    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "period_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CostPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private CostItemType itemType;

    /** {@code raw_material.id} hoặc {@code product_variant.id} tùy {@link #itemType}. */
    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "opening_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal openingQty = BigDecimal.ZERO;

    @Column(name = "opening_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal openingValue = BigDecimal.ZERO;

    @Column(name = "inbound_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal inboundQty = BigDecimal.ZERO;

    @Column(name = "inbound_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal inboundValue = BigDecimal.ZERO;

    @Column(name = "outbound_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal outboundQty = BigDecimal.ZERO;

    @Column(name = "outbound_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal outboundValue = BigDecimal.ZERO;

    @Column(name = "closing_qty", nullable = false, precision = 14, scale = 3)
    private BigDecimal closingQty = BigDecimal.ZERO;

    @Column(name = "closing_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal closingValue = BigDecimal.ZERO;

    /**
     * Đơn giá bình quân hiện hành (tạm trong kỳ mở; chính thức sau khóa sổ).
     * Null khi chưa có SL để chia.
     */
    @Column(name = "avg_unit_cost", precision = 14, scale = 2)
    private BigDecimal avgUnitCost;

    /** Tồn cuối = đầu + nhập − xuất; đơn giá tạm = (đầu + nhập) / (SL đầu + SL nhập). */
    public void refreshDerived() {
        closingQty = openingQty.add(inboundQty).subtract(outboundQty).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        closingValue =
                openingValue.add(inboundValue).subtract(outboundValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal qtyBase = openingQty.add(inboundQty);
        if (qtyBase.signum() > 0) {
            avgUnitCost = openingValue
                    .add(inboundValue)
                    .divide(qtyBase, MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            avgUnitCost = null;
        }
    }

    /**
     * Task 57 — khóa sổ: đơn giá chính thức, xuất giá theo ĐG đó, tồn cuối khớp
     * {@code (đầu + nhập) − xuất}. {@code fallbackAvg} dùng khi SL đầu + nhập = 0 (task 58).
     */
    public void finalizeForClose(BigDecimal fallbackAvg) {
        BigDecimal qtyBase = openingQty.add(inboundQty);
        if (qtyBase.signum() > 0) {
            avgUnitCost = openingValue
                    .add(inboundValue)
                    .divide(qtyBase, MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            avgUnitCost = fallbackAvg;
        }

        closingQty = openingQty.add(inboundQty).subtract(outboundQty).setScale(QTY_SCALE, RoundingMode.HALF_UP);

        if (closingQty.signum() < 0) {
            throw new IllegalStateException(
                    "Tồn cuối âm (itemType=" + itemType + ", itemId=" + itemId + ")");
        }

        if (avgUnitCost != null && closingQty.signum() > 0) {
            closingValue = closingQty.multiply(avgUnitCost).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            outboundValue = openingValue
                    .add(inboundValue)
                    .subtract(closingValue)
                    .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if (outboundValue.signum() < 0) {
                outboundValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
                closingValue = openingValue.add(inboundValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            }
        } else if (closingQty.signum() == 0) {
            closingValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            outboundValue = openingValue.add(inboundValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            // Có tồn cuối nhưng không có ĐG → giữ giá trị sổ tạm
            closingValue =
                    openingValue.add(inboundValue).subtract(outboundValue).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
    }

    public static CostItemBalance opening(
            CostPeriod period, CostItemType type, Long itemId, BigDecimal qty, BigDecimal value) {
        CostItemBalance row = new CostItemBalance();
        row.period = period;
        row.itemType = type;
        row.itemId = itemId;
        row.openingQty = scaleQty(qty);
        row.openingValue = scaleMoney(value);
        row.refreshDerived();
        return row;
    }

    /** Task 56 — cộng nhập (giá trị thật). */
    public void applyInbound(BigDecimal qty, BigDecimal value) {
        inboundQty = inboundQty.add(scaleQty(qty));
        inboundValue = inboundValue.add(scaleMoney(value));
        refreshDerived();
    }

    /** Task 56 — cộng xuất (giá trị tạm = SL × ĐG bình quân hiện hành). */
    public void applyOutbound(BigDecimal qty, BigDecimal unitCost) {
        BigDecimal q = scaleQty(qty);
        BigDecimal unit = unitCost == null ? BigDecimal.ZERO : unitCost;
        BigDecimal value = q.multiply(unit).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        outboundQty = outboundQty.add(q);
        outboundValue = outboundValue.add(value);
        refreshDerived();
    }

    static BigDecimal scaleQty(BigDecimal q) {
        return (q == null ? BigDecimal.ZERO : q).setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal scaleMoney(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
