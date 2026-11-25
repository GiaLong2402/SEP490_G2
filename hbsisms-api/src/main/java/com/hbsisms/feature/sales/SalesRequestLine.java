package com.hbsisms.feature.sales;

import com.hbsisms.feature.catalog.ProductVariant;
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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Dòng yêu cầu: SP + đơn vị (khay/thùng) + SL. */
@Entity
@Table(name = "sales_request_line")
@Getter
@Setter
@NoArgsConstructor
public class SalesRequestLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_request_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Setter(AccessLevel.PACKAGE)
    private SalesRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SellUnit unit = SellUnit.PACK;

    /** Số lượng theo {@link #unit}. */
    @Column(nullable = false)
    private int qty;

    /** Quy đổi ra khay (tồn/hold). */
    @Column(name = "qty_khay", nullable = false)
    private int qtyKhay;

    /** SL Sales xác nhận đáp ứng được (cùng đơn vị dòng). */
    @Column(name = "fulfillable_qty")
    private Integer fulfillableQty;

    /** Phần thiếu (cùng đơn vị dòng). */
    @Column(name = "shortage_qty")
    private Integer shortageQty;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
