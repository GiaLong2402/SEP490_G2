package com.hbsisms.feature.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SellPriceHistoryRepository extends JpaRepository<SellPriceHistory, Long> {

    Optional<SellPriceHistory> findFirstByVariant_IdAndEffectiveToIsNullOrderByEffectiveFromDesc(Long variantId);

    List<SellPriceHistory> findByVariant_IdOrderByEffectiveFromDesc(Long variantId);

    @Query(
            """
            select h from SellPriceHistory h
            join fetch h.variant v
            where v.product.id = :productId
            order by h.effectiveFrom desc, h.id desc
            """)
    List<SellPriceHistory> findByProductIdOrderByEffectiveFromDesc(@Param("productId") Long productId);
}
