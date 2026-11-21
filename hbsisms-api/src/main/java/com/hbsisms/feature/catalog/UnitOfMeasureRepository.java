package com.hbsisms.feature.catalog;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    Optional<UnitOfMeasure> findByCodeIgnoreCase(String code);

    List<UnitOfMeasure> findAllByOrderByCodeAsc();
}
