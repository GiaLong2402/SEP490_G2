package com.hbsisms.feature.catalog;

import com.hbsisms.common.persistence.AuditableEntity;
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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A sellable SKU (one pack size). Prices are per tray (khay); a case holds {@code packPerCase} trays. */
@Entity
@DynamicUpdate
@Table(name = "product_variant", indexes = @Index(name = "idx_variant_product", columnList = "product_id"))
@Getter
@Setter
@NoArgsConstructor
public class ProductVariant extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Product product;

    @Column(nullable = false, unique = true, length = 60)
    private String sku;

    /** e.g. "Khay 0,5kg". */
    @Column(nullable = false, length = 80)
    private String packLabel;

    @Column(name = "unit_weight_g")
    private Integer unitWeightG;

    /** List price per tray. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal listPrice;

    @Column(nullable = false)
    private short packPerCase = 1;

    @Column(length = 50)
    private String barcode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
