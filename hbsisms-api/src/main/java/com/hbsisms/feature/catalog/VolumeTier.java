package com.hbsisms.feature.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Quantity break: from {@code minQtyKhay} trays the unit price drops to {@code unitPrice}. */
@Entity
@Table(name = "volume_tier", uniqueConstraints =
        @UniqueConstraint(name = "uk_tier_variant_qty", columnNames = {"variant_id", "min_qty_khay"}))
@Getter
@Setter
@NoArgsConstructor
public class VolumeTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ProductVariant variant;

    @Column(nullable = false)
    private int minQtyKhay;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;
}
