package com.hbsisms.feature.catalog;

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

/** One BOM ingredient line for a finished product (qty per 1 finished unit). */
@Entity
@Table(name = "product_bom_line")
@Getter
@Setter
@NoArgsConstructor
public class ProductBomLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Raw material ingredient (preferred for DN). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_material_id")
    private RawMaterial rawMaterial;

    /** Optional intermediate / other finished product as component. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "component_product_id")
    private Product componentProduct;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id", nullable = false)
    private UnitOfMeasure unit;

    /** Waste % 0–100; null = 0. */
    @Column(name = "waste_percent", precision = 6, scale = 2)
    private BigDecimal wastePercent;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
