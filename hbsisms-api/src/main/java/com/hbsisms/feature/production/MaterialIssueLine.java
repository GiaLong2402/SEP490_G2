package com.hbsisms.feature.production;

import com.hbsisms.feature.inventory.RawMaterial;
import com.hbsisms.feature.inventory.RawMaterialLot;
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

/** Dòng phiếu xuất NVL. Lot / qtyIssued chỉ điền khi task 50 xác nhận. */
@Entity
@Table(name = "material_issue_line")
@Getter
@Setter
@NoArgsConstructor
public class MaterialIssueLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "material_issue_id", nullable = false)
    private MaterialIssue materialIssue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(name = "qty_required", nullable = false, precision = 14, scale = 4)
    private BigDecimal qtyRequired;

    /** Điền khi xác nhận xuất (task 50). */
    @Column(name = "qty_issued", precision = 14, scale = 4)
    private BigDecimal qtyIssued;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_lot_id")
    private RawMaterialLot rawMaterialLot;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
