package com.hbsisms.feature.inventory;

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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dòng phiếu nhập nguyên vật liệu (Inter 1 dòng 46–47). Lúc tạo phiếu chỉ có NVL + SL dự kiến;
 * khi xác nhận nhập kho mới điền SL thực nhận, lô, NSX/HSD, đơn giá và gắn lô NVL đã tạo.
 */
@Entity
@Table(name = "goods_receipt_line", indexes = @Index(name = "idx_grl_receipt", columnList = "goods_receipt_id"))
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceiptLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goods_receipt_id", nullable = false)
    private GoodsReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(nullable = false)
    private int lineNo;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyExpected;

    /** Trống khi phiếu còn chờ nhập. */
    @Column(precision = 12, scale = 3)
    private BigDecimal qtyReceived;

    @Column(length = 40)
    private String lotCode;

    private LocalDate mfgDate;

    private LocalDate expDate;

    @Column(precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raw_lot_id")
    private RawMaterialLot rawLot;
}
