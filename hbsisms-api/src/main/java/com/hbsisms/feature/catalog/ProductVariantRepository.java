package com.hbsisms.feature.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @EntityGraph(attributePaths = "product")
    Optional<ProductVariant> findWithProductById(Long id);

    @Override
    @EntityGraph(attributePaths = "product")
    List<ProductVariant> findAll(Sort sort);
}
