package com.hbsisms.feature.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByWarehouse_IdOrderByCodeAsc(Long warehouseId);

    boolean existsByWarehouse_IdAndCodeIgnoreCase(Long warehouseId, String code);

    boolean existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(Long warehouseId, String code, Long id);

    boolean existsByIdAndWarehouse_Id(Long id, Long warehouseId);

    @Query("select l from Location l join fetch l.warehouse order by l.warehouse.code asc, l.code asc")
    List<Location> findAllWithWarehouse();
}
