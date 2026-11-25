package com.hbsisms.feature.inventory;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByUnitRef_Id(Long unitId);

    Optional<RawMaterial> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<RawMaterial> findAllByOrderByCodeAsc();

    long countByStorageLocation_Id(Long locationId);

    List<RawMaterial> findByStorageLocationIsNull();

    /** Khóa dòng NVL khi cộng / trừ tồn theo lô (nhập kho, xuất cho sản xuất). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from RawMaterial m where m.id = :id")
    Optional<RawMaterial> findByIdForUpdate(@Param("id") Long id);
}
