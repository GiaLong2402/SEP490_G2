package com.hbsisms.feature.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {

    @Query(
            """
            select l from StocktakeLine l
            join fetch l.variant v
            join fetch v.product
            left join fetch l.lot
            left join fetch l.location loc
            left join fetch loc.warehouse
            where l.stocktake.id = :stocktakeId
            order by loc.code asc, v.sku asc
            """)
    List<StocktakeLine> findDetailedByStocktakeId(@Param("stocktakeId") Long stocktakeId);

    List<StocktakeLine> findByStocktake_Id(Long stocktakeId);
}
