package com.hbsisms.feature.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    long countByCategory_Id(Long categoryId);

    boolean existsByCategory_Id(Long categoryId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);
}
