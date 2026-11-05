package com.hbsisms.feature.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByWarehouse_IdOrderByCodeAsc(Long warehouseId);

    boolean existsByWarehouse_IdAndCodeIgnoreCase(Long warehouseId, String code);

    boolean existsByWarehouse_IdAndCodeIgnoreCaseAndIdNot(Long warehouseId, String code, Long id);

    boolean existsByIdAndWarehouse_Id(Long id, Long warehouseId);

    @Query("select l from Location l join fetch l.warehouse order by l.warehouse.code asc, l.code asc")
    List<Location> findAllWithWarehouse();

    /** Serialize capacity checks for concurrent inbound/move into the same location. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Location l join fetch l.warehouse where l.id = :id")
    Optional<Location> findByIdForUpdate(@Param("id") Long id);
}
