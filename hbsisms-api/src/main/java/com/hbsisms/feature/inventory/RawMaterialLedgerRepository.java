package com.hbsisms.feature.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RawMaterialLedgerRepository extends JpaRepository<RawMaterialLedger, Long> {

    List<RawMaterialLedger> findByRefTypeAndRefIdOrderByIdAsc(String refType, Long refId);
}
