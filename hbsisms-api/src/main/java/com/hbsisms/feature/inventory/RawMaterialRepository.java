package com.hbsisms.feature.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawMaterialRepository extends JpaRepository<RawMaterial, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByUnitRef_Id(Long unitId);

    Optional<RawMaterial> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<RawMaterial> findAllByOrderByCodeAsc();
}
