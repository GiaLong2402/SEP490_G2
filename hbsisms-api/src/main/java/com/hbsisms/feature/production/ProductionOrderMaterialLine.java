package com.hbsisms.feature.production;

import com.hbsisms.feature.inventory.RawMaterial;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot NVL cần dùng tại lúc tạo/refresh lệnh SX. */
@Entity
@Table(name = "production_order_material_line")
@Getter
@Setter
@NoArgsConstructor
public class ProductionOrderMaterialLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false)
    private ProductionOrder productionOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(name = "bom_qty_per_unit", nullable = false, precision = 14, scale = 4)
    private BigDecimal bomQtyPerUnit;

    @Column(name = "waste_percent", precision = 6, scale = 2)
    private BigDecimal wastePercent;

    @Column(name = "required_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal requiredQty;

    @Column(name = "on_hand_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal onHandQty;

    @Column(nullable = false)
    private boolean sufficient;

    @Column(name = "line_estimated_cost", precision = 14, scale = 2)
    private BigDecimal lineEstimatedCost = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
