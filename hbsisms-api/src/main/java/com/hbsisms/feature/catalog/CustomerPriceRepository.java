package com.hbsisms.feature.catalog;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    Optional<CustomerPrice> findByCustomerIdAndVariantId(Long customerId, Long variantId);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerPrice> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<CustomerPrice> findByCustomerIdOrderByIdAsc(Long customerId);

    /**
     * Giá riêng đang áp dụng: đã từng duyệt ({@code approvedAt}) và còn trong cửa sổ hiệu lực.
     * Khớp FE mock — kể cả khi đang có đề xuất pending mới (vẫn bán theo {@code unitPrice} cũ).
     */
    @Query("""
            select p from CustomerPrice p
            where p.customer.id = :customerId
              and p.variant.id = :variantId
              and p.approvedAt is not null
              and (p.effectiveFrom is null or p.effectiveFrom <= :today)
              and (p.effectiveTo is null or p.effectiveTo >= :today)
            """)
    Optional<CustomerPrice> findEffectiveSpecial(
            @Param("customerId") Long customerId,
            @Param("variantId") Long variantId,
            @Param("today") LocalDate today);
}
