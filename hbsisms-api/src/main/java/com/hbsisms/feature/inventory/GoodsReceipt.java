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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Goods receipt header; its lines are {@code stock_ledger} rows with {@code refType = goods_receipt}. */
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

    @PrePersist
    void defaultReceivedAt() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}
