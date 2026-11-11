package com.hbsisms.feature.sales;

import com.hbsisms.common.persistence.AuditableEntity;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerAddress;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.payment.PaymentStatus;
import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Every order: counter retail, B2B, and online B2C ({@code orderType}).
 * {@code placedBy} is null when a B2C customer ordered by themselves.
 */
@Entity
@DynamicUpdate
@Table(name = "sales_order", indexes = {
        @Index(name = "idx_order_customer", columnList = "customer_id"),
        @Index(name = "idx_order_state", columnList = "state"),
        @Index(name = "idx_order_type", columnList = "order_type"),
        @Index(name = "idx_order_hold_expires", columnList = "hold_expires_at")})
@Getter
@Setter
@NoArgsConstructor
public class SalesOrder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SaleChannel channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_quote_id")
    private Quote sourceQuote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(nullable = false)
    private short paymentTermDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AllocationStatus allocationStatus = AllocationStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.PICKUP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_address_id")
    private CustomerAddress shipAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderState state = OrderState.PENDING;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 40)
    private String voucherCode;

    /** B2B order pushing the customer over their credit limit — needs owner approval. */
    @Column(name = "is_over_limit", nullable = false)
    private boolean overLimit;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal overLimitAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placed_by")
    private AppUser placedBy;

    @Column(nullable = false)
    private Instant placedAt;

    /**
     * Hết hạn giữ hàng (đơn online chờ thanh toán, mặc định +15 phút).
     * {@code null} = không áp dụng (quầy / đã Paid / không hold).
     */
    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(columnDefinition = "text")
    private String note;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(lombok.AccessLevel.NONE)
    private List<SalesOrderLine> lines = new ArrayList<>();

    public List<SalesOrderLine> getLines() {
        return List.copyOf(lines);
    }

    public void addLine(SalesOrderLine line) {
        line.setOrder(this);
        lines.add(line);
    }

    public void removeLine(SalesOrderLine line) {
        lines.remove(line);
        line.setOrder(null);
    }

    public void clearLines() {
        for (SalesOrderLine line : new ArrayList<>(lines)) {
            removeLine(line);
        }
    }

    @PrePersist
    void defaultPlacedAt() {
        if (placedAt == null) {
            placedAt = Instant.now();
        }
    }
}
