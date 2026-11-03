package com.hbsisms.feature.customer;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Shipping / billing address of a customer; {@code inDeliveryRadius} = the shop delivers it itself. */
@Entity
@Table(name = "customer_address", indexes = @Index(name = "idx_address_customer", columnList = "customer_id"))
@Getter
@Setter
@NoArgsConstructor
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AddressKind kind = AddressKind.SHIPPING;

    @Column(length = 160)
    private String recipientName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 255)
    private String line1;

    @Column(length = 120)
    private String ward;

    @Column(length = 120)
    private String district;

    @Column(length = 120)
    private String province;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean inDeliveryRadius;

    @Column(precision = 6, scale = 2)
    private BigDecimal distanceKm;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
