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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phiếu nhập. Thành phẩm: dòng là {@code stock_ledger} ({@code refType = goods_receipt}), nhập ngay.
 * Nguyên liệu: dòng ở {@link GoodsReceiptLine}; tạo phiếu ở trạng thái Chờ nhập, xác nhận mới cộng tồn theo lô.
 */
@Entity
@Table(name = "goods_receipt")
@Getter
@Setter
@NoArgsConstructor
public class GoodsReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptType receiptType = ReceiptType.FINISHED;

    @Column(length = 200)
    private String source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private AppUser receivedBy;

    /** Defaults to insert time; can be back-dated. */
    @Column(nullable = false)
    private Instant receivedAt;

    @Column(columnDefinition = "text")
    private String note;

    /** Trống ở phiếu tạo trước Inter 1 dòng 46 → coi là đã hoàn thành (xem {@link #getStatus()}). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReceiptStatus status;

    /** Ngày hàng dự kiến về (phiếu chờ nhập). */
    private LocalDate expectedDate;

    /** Lúc xác nhận nhập kho (cộng tồn) hoặc hủy phiếu. */
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private AppUser completedBy;

    public ReceiptStatus getStatus() {
        return status == null ? ReceiptStatus.COMPLETED : status;
    }

    @PrePersist
    void defaultReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
