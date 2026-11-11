package com.hbsisms.feature.payment;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.sales.SalesOrder;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A money movement (in, or refund out). PayOS gateway details are kept on the same row
 * ({@code payosOrderCode}, {@code checkoutUrl}, {@code rawPayload}).
 */
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order", columnList = "order_id"),
        @Index(name = "idx_payment_customer", columnList = "customer_id")})
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private SalesOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PayDirection direction = PayDirection.IN;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayTxnStatus status = PayTxnStatus.SUCCESS;

    /** Manual owner mark: thiếu / thừa / trùng (Excel #39). */
    @Enumerated(EnumType.STRING)
    @Column(name = "reconcile_flag", nullable = false, length = 20)
    private PaymentReconcileFlag reconcileFlag = PaymentReconcileFlag.NONE;

    @Column(length = 120)
    private String reference;

    /** payos | bank | cash */
    @Column(length = 30)
    private String gateway;

    @Column(unique = true)
    private Long payosOrderCode;

    @Column(columnDefinition = "text")
    private String checkoutUrl;

    /** Raw gateway webhook / response, kept for reconciliation. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> rawPayload;

    /** Null while gateway payment is still PENDING (PayOS). */
    @Column(nullable = true)
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private AppUser receivedBy;

    @Column(length = 200)
    private String note;

    public Map<String, Object> getRawPayload() {
        return rawPayload == null ? null : Map.copyOf(rawPayload);
    }

    public void setRawPayload(Map<String, Object> rawPayload) {
        this.rawPayload = rawPayload == null ? null : new HashMap<>(rawPayload);
    }
}
