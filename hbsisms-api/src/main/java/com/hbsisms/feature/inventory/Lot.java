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
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A production / receiving batch of one variant. {@code expDate} drives FEFO picking. */
@Entity
@Table(name = "lot", indexes = @Index(name = "idx_lot_variant_exp", columnList = "variant_id, exp_date"))
@Getter
@Setter
@NoArgsConstructor
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String lotCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    private LocalDate mfgDate;

    private LocalDate expDate;

    private Instant receivedAt;

    @Column(length = 200)
    private String note;
}
