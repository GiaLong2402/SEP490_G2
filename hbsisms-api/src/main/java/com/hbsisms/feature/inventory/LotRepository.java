package com.hbsisms.feature.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByLotCodeIgnoreCase(String lotCode);
}
