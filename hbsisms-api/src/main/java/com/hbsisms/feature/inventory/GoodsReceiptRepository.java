package com.hbsisms.feature.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface GoodsReceiptRepository
        extends JpaRepository<GoodsReceipt, Long>, JpaSpecificationExecutor<GoodsReceipt> {

    @Query("select max(g.code) from GoodsReceipt g where g.code like 'PN-%'")
    Optional<String> findMaxPnCode();
}
