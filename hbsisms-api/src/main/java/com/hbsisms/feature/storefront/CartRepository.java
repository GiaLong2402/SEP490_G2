package com.hbsisms.feature.storefront;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<Cart> findWithItemsBySessionTokenAndStatus(UUID sessionToken, CartStatus status);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<Cart> findWithItemsByIdAndStatus(Long id, CartStatus status);
}
