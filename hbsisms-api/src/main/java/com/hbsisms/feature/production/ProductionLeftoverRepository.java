package com.hbsisms.feature.production;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionLeftoverRepository extends JpaRepository<ProductionLeftover, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "lot"})
    List<ProductionLeftover> findByProductionOrder_IdOrderByIdAsc(Long productionOrderId);
}
