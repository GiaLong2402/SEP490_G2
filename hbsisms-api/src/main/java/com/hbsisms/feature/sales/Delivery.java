package com.hbsisms.feature.sales;

import com.hbsisms.feature.customer.CustomerAddress;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

/** One delivery leg of an order (B2B orders can ship in several legs). Shipped quantities live in {@code items}. */
@Entity
@Table(name = "delivery", indexes = {
        @Index(name = "idx_delivery_order", columnList = "order_id"),
        @Index(name = "idx_delivery_assignee", columnList = "assigned_to")})
@Getter
@Setter
@NoArgsConstructor
public class Delivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private SalesOrder order;

    /** e.g. "Đợt 1/2" or "Trọn đơn". */
    @Column(length = 40)
    private String legLabel;

    private LocalDate scheduledDate;

    @Column(length = 40)
    private String timeWindow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.SHOP_DELIVERY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private CustomerAddress address;

    @Column(nullable = false)
    private boolean inRadius;

    @Column(precision = 6, scale = 2)
    private BigDecimal distanceKm;

    /** Staff driver when the shop delivers itself. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private AppUser assignedTo;

    /** Partner carrier name. */
    @Column(length = 120)
    private String carrier;

    @JdbcTypeCode(SqlTypes.JSON)
    private List<DeliveryItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryState state = DeliveryState.READY;

    private Instant deliveredAt;

    @Column(length = 255)
    private String note;

    public List<DeliveryItem> getItems() {
        return items == null ? List.of() : List.copyOf(items);
    }

    public void setItems(List<DeliveryItem> items) {
        this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
    }
}
