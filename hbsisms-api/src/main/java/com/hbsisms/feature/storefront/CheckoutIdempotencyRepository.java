package com.hbsisms.feature.storefront;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, Long> {

    Optional<CheckoutIdempotency> findByUserIdAndIdemKey(Long userId, String idemKey);
}
