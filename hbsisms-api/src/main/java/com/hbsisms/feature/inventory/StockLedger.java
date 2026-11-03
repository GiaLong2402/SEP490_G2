package com.hbsisms.feature.inventory;

import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.user.AppUser;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

/**
 * Append-only stock movement journal (in / out / hold / release / move / adjust / damage / expire).
 * Replaces separate stock_move, allocation and receipt-line tables; {@code refType}/{@code refId} point at the source document.
 */
@Entity
@Immutable
@Table(name = "stock_ledger", indexes = {
        @Index(name = "idx_ledger_variant_time", columnList = "variant_id, created_at"),
        @Index(name = "idx_ledger_ref", columnList = "ref_type, ref_id")})
@Getter
@Setter
@NoArgsConstructor
public class StockLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    /** Destination when {@code entryType = MOVE}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id")
    private Location toLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerEntryType entryType;

    /** +in / −out, in trays. */
    @Column(nullable = false)
    private int qtyDelta;

    private Integer balanceAfter;

    /** sales_order_line | goods_receipt | damage_report … */
    @Column(length = 40)
    private String refType;

    private Long refId;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
