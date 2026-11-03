package com.hbsisms.feature.role;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<AppRole, Short> {

    Optional<AppRole> findByCode(String code);

    boolean existsByCode(String code);

    List<AppRole> findAllByCodeNotOrderByIdAsc(String code);
}
