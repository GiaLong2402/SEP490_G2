package com.hbsisms.feature.inventory;

import com.hbsisms.feature.catalog.ProductVariant;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Current balance (in trays) per variant × lot × location. Maintained alongside {@link StockLedger},
 * which is the immutable history; quantities can never go negative.
 */
@Entity
@Table(name = "stock",
        uniqueConstraints = @UniqueConstraint(name = "uk_stock_variant_lot_location",
                columnNames = {"variant_id", "lot_id", "location_id"}),
        indexes = @Index(name = "idx_stock_variant", columnList = "variant_id"))
@Check(name = "chk_stock_nonneg",
        constraints = "qty_available >= 0 AND qty_held >= 0 AND qty_quarantine >= 0 AND qty_expired >= 0")
@Getter
@Setter
@NoArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(nullable = false)
    private int qtyAvailable;

    /** Reserved for confirmed orders, not yet shipped. */
    @Column(nullable = false)
    private int qtyHeld;

    @Column(nullable = false)
    private int qtyQuarantine;

    @Column(nullable = false)
    private int qtyExpired;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
