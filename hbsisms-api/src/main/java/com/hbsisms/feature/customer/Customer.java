package com.hbsisms.feature.customer;

import com.hbsisms.common.persistence.AuditableEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A buyer: B2C individual or B2B business. Only B2B customers get credit terms.
 * {@code currentDebt} is the running balance of {@code customer_debt_ledger}.
 */
@Entity
@DynamicUpdate
@Table(name = "customer", indexes = @Index(name = "idx_customer_account", columnList = "account_user_id"))
@Check(name = "chk_b2b_terms", constraints = "customer_type = 'B2B' OR (credit_limit = 0 AND net_days = 0)")
@Getter
@Setter
@NoArgsConstructor
public class Customer extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CustomerType customerType = CustomerType.B2C;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 160)
    private String email;

    @Column(length = 20)
    private String phone;

    /** MST — B2B only. */
    @Column(length = 20)
    private String taxCode;

    @Column(length = 160)
    private String contactName;

    @Column(length = 20)
    private String contactPhone;

    /** Login account for the B2C portal. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_user_id")
    private AppUser accountUser;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit = BigDecimal.ZERO;

    @Column(nullable = false)
    private short netDays;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal currentDebt = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String note;
}
