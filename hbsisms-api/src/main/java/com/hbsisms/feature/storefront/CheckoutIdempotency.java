package com.hbsisms.feature.storefront;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Maps (user, Idempotency-Key) → created checkout order so retries return the same đơn. */
@Entity
@Table(
        name = "checkout_idempotency",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_checkout_idem_user_key",
                        columnNames = {"user_id", "idem_key"}))
@Getter
@Setter
@NoArgsConstructor
public class CheckoutIdempotency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idem_key", nullable = false, length = 100)
    private String idemKey;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(lombok.AccessLevel.NONE)
    private Instant createdAt;
}
