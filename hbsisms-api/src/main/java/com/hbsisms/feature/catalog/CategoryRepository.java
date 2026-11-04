package com.hbsisms.feature.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    Optional<Category> findByCodeIgnoreCase(String code);

    @Override
    @EntityGraph(attributePaths = "parent")
    List<Category> findAll(Sort sort);

    @EntityGraph(attributePaths = "parent")
    Optional<Category> findWithParentById(Long id);
}
