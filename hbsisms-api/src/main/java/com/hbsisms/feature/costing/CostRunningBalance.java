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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * WAC liên hoàn ngoài sổ kỳ: GT/SL tồn hiện hành theo mặt hàng (NVL hoặc TP).
 * Cập nhật mỗi nhập/xuất; kỳ OPEN chỉ mirror báo cáo.
 */
@Entity
@Table(
        name = "cost_running_balance",
        indexes = @Index(name = "idx_cost_run_item", columnList = "item_type, item_id"),
        uniqueConstraints =
                @UniqueConstraint(name = "uk_cost_run_item", columnNames = {"item_type", "item_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CostRunningBalance {

    private static final int QTY_SCALE = 3;
    private static final int MONEY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private CostItemType itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "qty_on_hand", nullable = false, precision = 14, scale = 3)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(name = "inventory_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal inventoryValue = BigDecimal.ZERO;

    @Column(name = "avg_unit_cost", precision = 14, scale = 2)
    private BigDecimal avgUnitCost;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static CostRunningBalance empty(CostItemType type, Long itemId) {
        CostRunningBalance row = new CostRunningBalance();
        row.itemType = type;
        row.itemId = itemId;
        row.qtyOnHand = BigDecimal.ZERO.setScale(QTY_SCALE, RoundingMode.HALF_UP);
        row.inventoryValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        row.updatedAt = Instant.now();
        return row;
    }

    public static CostRunningBalance of(CostItemType type, Long itemId, BigDecimal qty, BigDecimal value) {
        CostRunningBalance row = empty(type, itemId);
        row.qtyOnHand = scaleQty(qty);
        row.inventoryValue = scaleMoney(value);
        row.refreshAvg();
        return row;
    }

    /** Nhập: GT += SL×ĐG nhập; ĐG = GT/SL. */
    public void applyInbound(BigDecimal qty, BigDecimal unitPrice) {
        BigDecimal q = scaleQty(qty);
        if (q.signum() <= 0) {
            return;
        }
        BigDecimal price = unitPrice == null ? BigDecimal.ZERO : unitPrice;
        BigDecimal value = q.multiply(price).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        qtyOnHand = qtyOnHand.add(q).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        inventoryValue = inventoryValue.add(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        refreshAvg();
    }

    /**
     * Xuất theo ĐG WAC hiện hành. Trả về đơn giá dùng để trừ GT.
     * SL/GT không âm.
     */
    public BigDecimal applyOutbound(BigDecimal qty) {
        BigDecimal q = scaleQty(qty);
        if (q.signum() <= 0) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal unit = avgUnitCost == null ? BigDecimal.ZERO : avgUnitCost;
        if (q.compareTo(qtyOnHand) > 0) {
            q = qtyOnHand;
        }
        BigDecimal value = q.multiply(unit).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        qtyOnHand = qtyOnHand.subtract(q).setScale(QTY_SCALE, RoundingMode.HALF_UP);
        inventoryValue = inventoryValue.subtract(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (qtyOnHand.signum() <= 0) {
            qtyOnHand = BigDecimal.ZERO.setScale(QTY_SCALE, RoundingMode.HALF_UP);
            inventoryValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            avgUnitCost = null;
        } else if (inventoryValue.signum() < 0) {
            inventoryValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            refreshAvg();
        } else {
            refreshAvg();
        }
        updatedAt = Instant.now();
        return unit.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public void refreshAvg() {
        if (qtyOnHand.signum() > 0) {
            avgUnitCost = inventoryValue.divide(qtyOnHand, MONEY_SCALE, RoundingMode.HALF_UP);
        } else {
            avgUnitCost = null;
            qtyOnHand = BigDecimal.ZERO.setScale(QTY_SCALE, RoundingMode.HALF_UP);
            inventoryValue = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        updatedAt = Instant.now();
    }

    static BigDecimal scaleQty(BigDecimal q) {
        return (q == null ? BigDecimal.ZERO : q).setScale(QTY_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal scaleMoney(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
