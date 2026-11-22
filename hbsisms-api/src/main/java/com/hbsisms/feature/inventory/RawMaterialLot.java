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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

/**
 * Lô nguyên vật liệu (Inter 1 dòng 47): mỗi lần xác nhận nhập tạo một lô có HSD và đơn giá nhập.
 * {@code qtyRemaining} giảm khi xuất cho sản xuất (dòng 50 — ưu tiên lô sắp hết hạn trước).
 * Tổng {@code qtyRemaining} các lô = {@link RawMaterial#getQtyOnHand()}.
 */
@Entity
@Table(
        name = "raw_material_lot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_raw_lot_material_code", columnNames = {"raw_material_id", "lot_code"}),
        indexes = @Index(name = "idx_raw_lot_material_exp", columnList = "raw_material_id, exp_date"))
@Check(name = "chk_raw_lot_qty", constraints = "qty_received >= 0 and qty_remaining >= 0")
@Getter
@Setter
@NoArgsConstructor
public class RawMaterialLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "raw_material_id", nullable = false)
    private RawMaterial rawMaterial;

    @Column(name = "lot_code", nullable = false, length = 40)
    private String lotCode;

    private LocalDate mfgDate;

    /** Có thể trống: NVL không có hạn sử dụng (bao bì, đá…). */
    @Column(name = "exp_date")
    private LocalDate expDate;

    /** Đơn giá nhập / 1 đơn vị NVL. Trống chỉ với lô tồn đầu kỳ (trước khi quản lý theo lô). */
    @Column(precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyReceived = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyRemaining = BigDecimal.ZERO;

    /** Phiếu nhập sinh ra lô (trống với lô tồn đầu kỳ). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "goods_receipt_id")
    private GoodsReceipt receipt;

    @Column(nullable = false)
    private Instant receivedAt;

    @Column(length = 255)
    private String note;

    @PrePersist
    void defaultReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
