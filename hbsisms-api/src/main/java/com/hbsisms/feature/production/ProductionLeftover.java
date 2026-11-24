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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** NVL dư trả lại kho thành lô {@code <lô gốc>-TL} khi báo hoàn thành SX. */
@Entity
@Table(name = "production_leftover")
@Getter
@Setter
@NoArgsConstructor
public class ProductionLeftover {

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private RawMaterialLot lot;

    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal qty;

    @Column(name = "exp_date", nullable = false)
    private LocalDate expDate;

    /** qty × đơn giá lô gốc — phần trừ khỏi chi phí NVL. */
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
}
