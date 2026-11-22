package com.hbsisms.feature.inventory;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Sổ kho nguyên vật liệu: mỗi lần cộng / trừ lô NVL ghi một dòng (truy vết nhập — xuất — tồn). */
@Entity
@Table(
        name = "raw_material_ledger",
        indexes = {
            @Index(name = "idx_raw_ledger_material", columnList = "raw_material_id, created_at"),
            @Index(name = "idx_raw_ledger_ref", columnList = "ref_type, ref_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class RawMaterialLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_lot_id")
    private RawMaterialLot lot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RawLedgerEntryType entryType;

    /** Dương = nhập, âm = xuất. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyDelta;

    /** Tổng tồn của NVL sau phát sinh. */
    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal balanceAfter;

    @Column(precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "ref_type", length = 40)
    private String refType;

    @Column(name = "ref_id")
    private Long refId;

    @Column(length = 255)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void defaultCreatedAt() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
