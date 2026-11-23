package com.hbsisms.feature.production;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductionOrderMaterialLineRepository extends JpaRepository<ProductionOrderMaterialLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "rawMaterial.unitRef"})
    List<ProductionOrderMaterialLine> findByProductionOrder_IdOrderBySortOrderAscIdAsc(Long productionOrderId);
}
