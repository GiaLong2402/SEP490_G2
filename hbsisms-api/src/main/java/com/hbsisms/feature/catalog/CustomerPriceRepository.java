package com.hbsisms.feature.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerPriceRepository extends JpaRepository<CustomerPrice, Long> {

    Optional<CustomerPrice> findByCustomerIdAndVariantId(Long customerId, Long variantId);

    @EntityGraph(attributePaths = "customer")
    Optional<CustomerPrice> findWithCustomerById(Long id);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<CustomerPrice> findByCustomerIdOrderByIdAsc(Long customerId);
}
