package com.hbsisms.feature.sales;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesRequestLineRepository extends JpaRepository<SalesRequestLine, Long> {

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<SalesRequestLine> findByRequest_IdOrderBySortOrderAscIdAsc(Long requestId);
}
