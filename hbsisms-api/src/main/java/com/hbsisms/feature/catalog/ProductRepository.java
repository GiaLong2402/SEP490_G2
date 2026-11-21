package com.hbsisms.feature.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    long countByCategory_Id(Long categoryId);

    boolean existsByCategory_Id(Long categoryId);

    boolean existsByUnit_Id(Long unitId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<Product> findWithCategoryById(Long id);

    @EntityGraph(attributePaths = {"category", "unit"})
    Optional<Product> findWithCategoryBySlugIgnoreCase(String slug);

    Optional<Product> findByCodeIgnoreCase(String code);
}
