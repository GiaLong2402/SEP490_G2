package com.hbsisms.feature.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBomLineRepository extends JpaRepository<ProductBomLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "componentProduct", "unit"})
    List<ProductBomLine> findByProduct_IdOrderBySortOrderAscIdAsc(Long productId);

    void deleteByProduct_Id(Long productId);

    boolean existsByUnit_Id(Long unitId);
}
